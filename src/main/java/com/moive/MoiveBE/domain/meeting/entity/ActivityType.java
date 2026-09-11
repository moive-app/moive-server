package com.moive.MoiveBE.domain.meeting.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActivityType {
    BOARD_GAME("보드게임"),
    BOWLING("볼링"),
    ESCAPE_ROOM("방탈출"),
    KARAOKE("노래방"),
    PC_ROOM("PC방"),
    COMIC_CAFE("만화카페"),
    KOREAN_FOOD("한식"),
    WESTERN_FOOD("양식"),
    CHINESE_FOOD("중식"),
    JAPANESE_FOOD("일식"),
    MEAT("고기"),
    SEAFOOD("해산물"),
    PARK("공원"),
    WALK("산책"),
    HIKING("등산"),
    SHOPPING("쇼핑"),
    CAFE("카페"),
    DESSERT("디저트"),
    IZAKAYA("이자카야"),
    COCKTAIL_BAR("칵테일바"),
    MOVIE("영화"),
    EXHIBITION("전시/미술관");

    private final String label;
}
