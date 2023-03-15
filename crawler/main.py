import json
import logging
import os
import sys
from time import sleep

import pika

import undetected_chromedriver as uc
from pika import PlainCredentials

queue_in = "crawler_task"
queue_out = "crawler_result"

logging.basicConfig(level=logging.INFO)
# demo to use redis stream
def main():
    pika_host = os.getenv("PIKA_HOST")
    pika_port = int(os.getenv("PIKA_PORT"))
    pika_user = os.getenv("PIKA_USER")
    pika_pass = os.getenv("PIKA_PASS")
    credentials = PlainCredentials(pika_user, pika_pass)

    connection = pika.BlockingConnection(
        pika.ConnectionParameters(host=pika_host, port=pika_port, credentials=credentials))
    channel = connection.channel()

    channel.queue_declare(queue=queue_in, durable=True)
    channel.queue_declare(queue=queue_out, durable=True)

    def callback(ch, method, properties, body):
        logging.info("received a message from queue %s", queue_in)
        logging.info("starting chromedriver")
        options = uc.ChromeOptions()
        options.add_argument("--disable-gpu")
        options.add_argument('--no-sandbox')
        # comment driver_executable_path when you test in local
        driver = uc.Chrome(version_main=111, options=options, headless=True,
                           driver_executable_path="/usr/bin/chromedriver")
        json_obj = json.loads(body)

        url = json_obj["url"]
        task_id = json_obj["task_id"]
        logging.info("task_id is %s, url is %s", task_id, url)

        driver.get(url)
        sleep(12)
        page_source = driver.page_source
        logging.info("get page_source success")

        out_obj = {
            "task_id": task_id,
            "page_source": page_source
        }
        channel.basic_publish(exchange='', routing_key=queue_out, body=json.dumps(out_obj, ensure_ascii=False))
        logging.info("send message to queue %s", queue_out)
        driver.close()

    logging.info("start consuming at queue %s", queue_in)
    channel.basic_consume(queue=queue_in, on_message_callback=callback, auto_ack=True)

    channel.start_consuming()


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print('Interrupted')
        try:
            sys.exit(0)
        except SystemExit:
            os.close(0)
