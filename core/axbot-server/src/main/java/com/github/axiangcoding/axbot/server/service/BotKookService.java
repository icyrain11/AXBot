package com.github.axiangcoding.axbot.server.service;


import com.github.axiangcoding.axbot.engine.entity.AxBotSystemEvent;
import com.github.axiangcoding.axbot.engine.entity.kook.AxBotSysInputForKook;
import com.github.axiangcoding.axbot.engine.entity.kook.AxBotSysOutputForKook;
import com.github.axiangcoding.axbot.engine.entity.kook.AxBotUserInputForKook;
import com.github.axiangcoding.axbot.engine.entity.kook.AxBotUserOutputForKook;
import com.github.axiangcoding.axbot.remote.bilibili.BiliClient;
import com.github.axiangcoding.axbot.remote.bilibili.service.entity.BiliResponse;
import com.github.axiangcoding.axbot.remote.bilibili.service.entity.resp.RoomInfoData;
import com.github.axiangcoding.axbot.remote.kook.KookClient;
import com.github.axiangcoding.axbot.remote.kook.entity.KookEvent;
import com.github.axiangcoding.axbot.remote.kook.service.entity.req.CreateMessageReq;
import com.github.axiangcoding.axbot.server.cache.CacheKeyGenerator;
import com.github.axiangcoding.axbot.server.configuration.props.BotConfProps;
import com.github.axiangcoding.axbot.server.controller.entity.vo.req.KookWebhookEvent;
import com.github.axiangcoding.axbot.server.data.entity.KookGuildSetting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class BotKookService {
    @Resource
    KookClient kookClient;

    @Resource
    BotConfProps botConfProps;

    @Resource
    AxBotService axBotService;

    @Resource
    KookGuildSettingService kookGuildSettingService;

    @Resource
    BiliClient biliClient;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    public boolean compareVerifyToken(String retToken) {
        return StringUtils.equals(retToken, botConfProps.getKook().getVerifyToken());
    }

    /**
     * 判断回调消息类型要进入哪个处理流程
     *
     * @param event
     * @return
     */
    public Map<String, Object> DetermineMessageResponse(KookWebhookEvent event) {
        HashMap<String, Object> map = new HashMap<>();
        KookEvent d = event.getD();
        if (Objects.equals(d.getType(), KookEvent.TYPE_TEXT) ||
                Objects.equals(d.getType(), KookEvent.TYPE_KMARKDOWN)) {
            String content = d.getContent();
            String[] contentSplit = StringUtils.split(content);

            String command = "";
            List<String> prefixes = botConfProps.getTriggerMessagePrefix();
            boolean isTrigger = false;
            for (String prefix : prefixes) {
                if (prefix.equals(contentSplit[0])) {
                    isTrigger = true;
                    command = StringUtils.join(Arrays.copyOfRange(contentSplit, 1, contentSplit.length), " ");
                    break;
                }
            }
            if (isTrigger) {
                log.info("received trigger message from kook. user: [{}], message content: [{}]",
                        d.getAuthorId(), d.getContent());
                String guildId = d.getExtra().getGuildId();
                Optional<KookGuildSetting> optKgs = kookGuildSettingService.findBytGuildId(guildId);
                if (optKgs.isEmpty()) {
                    kookGuildSettingService.updateWhenJoin(guildId);
                } else {
                    // TODO 增加使用量
                }
                AxBotUserInputForKook input = new AxBotUserInputForKook();
                input.setFromUserId(d.getAuthorId());
                input.setRequestCommand(command);
                input.setFromMsgId(d.getMsgId());
                input.setFromChannel(d.getTargetId());
                input.setFromGuild(guildId);
                axBotService.genResponseForInputAsync(AxBotService.PLATFORM_KOOK, input, output -> {
                    if (output == null) {
                        return;
                    }
                    AxBotUserOutputForKook out = ((AxBotUserOutputForKook) output);
                    CreateMessageReq req = new CreateMessageReq();
                    req.setType(KookEvent.TYPE_CARD);
                    req.setQuote(out.getReplayToMsg());
                    req.setTargetId(out.getToChannel());
                    req.setContent(out.getContent());
                    kookClient.createMessage(req);
                });
            }
        } else if (Objects.equals(d.getType(), KookEvent.TYPE_SYSTEM_MESSAGE)) {

            String channelType = d.getChannelType();
            if (Objects.equals(channelType, KookEvent.CHANNEL_TYPE_WEBHOOK_CHALLENGE)) {
                String challenge = d.getChallenge();
                map.put("challenge", challenge);
            } else if (Objects.equals(channelType, KookEvent.CHANNEL_TYPE_PERSON)) {
                AxBotSysInputForKook input = new AxBotSysInputForKook();
                String type = d.getExtra().getType();
                if ("self_joined_guild".equals(type)) {
                    input.setEvent(AxBotSystemEvent.SYSTEM_EVENT_JOIN_GUILD);
                    String guildId = (String) d.getExtra().getBody().get("guild_id");
                    input.setFromGuild(guildId);
                } else if ("self_exited_guild".equals(type)) {
                    input.setEvent(AxBotSystemEvent.SYSTEM_EVENT_EXIT_GUILD);
                    String guildId = (String) d.getExtra().getBody().get("guild_id");
                    input.setFromGuild(guildId);
                } else {
                    return map;
                }

                axBotService.genResponseForSystemAsync(AxBotService.PLATFORM_KOOK, input, output -> {
                    if (output == null) {
                        return;
                    }
                    AxBotSysOutputForKook out = ((AxBotSysOutputForKook) output);
                    CreateMessageReq req = new CreateMessageReq();
                    req.setType(KookEvent.TYPE_CARD);
                    req.setTargetId(out.getToChannel());
                    req.setContent(out.getContent());
                    kookClient.createMessage(req);

                });
            }
        } else {
            // do nothing yet
        }
        return map;
    }

    public void checkBiliRoomStatus() {
        List<KookGuildSetting> settings = kookGuildSettingService.findByEnabledBiliLiveReminder();

        settings.forEach(setting -> {
            String biliRoomId = setting.getFunctionSetting().getBiliRoomId();
            if(!StringUtils.isNumeric(biliRoomId)) {
                return;
            }
            String biliLiveChannelId = setting.getFunctionSetting().getBiliLiveChannelId();
            BiliResponse<RoomInfoData> liveRoomInfo = biliClient.getLiveRoomInfo(biliRoomId);

            String cacheKey = CacheKeyGenerator.getBiliRoomRemindCacheKey(biliLiveChannelId, biliRoomId);

            AxBotSysInputForKook input = new AxBotSysInputForKook();
            input.setEvent(AxBotSystemEvent.SYSTEM_EVENT_BILI_ROOM_REMIND);
            HashMap<String, Object> extraMap = new HashMap<>();
            RoomInfoData roomInfoData = liveRoomInfo.getData();
            extraMap.put("roomId", roomInfoData.getRoomId());
            extraMap.put("title", roomInfoData.getTitle());
            extraMap.put("areaName", roomInfoData.getAreaName());
            extraMap.put("description", roomInfoData.getDescription());

            input.setExtraMap(extraMap);
            if (roomInfoData.getLiveStatus() == 1) {
                if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey))) {
                    axBotService.genResponseForSystemAsync(AxBotService.PLATFORM_KOOK, input, output -> {
                        if (output == null) {
                            return;
                        }
                        AxBotSysOutputForKook out = ((AxBotSysOutputForKook) output);
                        CreateMessageReq req = new CreateMessageReq();
                        req.setType(KookEvent.TYPE_CARD);
                        req.setTargetId(biliLiveChannelId);
                        req.setContent(out.getContent());
                        kookClient.createMessage(req);
                    });
                }
                stringRedisTemplate.opsForValue().set(cacheKey, "", 10, TimeUnit.MINUTES);
            }
        });
    }

}
