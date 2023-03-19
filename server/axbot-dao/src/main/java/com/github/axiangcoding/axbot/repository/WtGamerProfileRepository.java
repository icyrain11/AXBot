package com.github.axiangcoding.axbot.repository;

import com.github.axiangcoding.axbot.entity.WtGamerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WtGamerProfileRepository extends JpaRepository<WtGamerProfile, Long> {
    Optional<WtGamerProfile> findByNickname(String nickname);
}