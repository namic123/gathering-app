package com.moim.moimbackend.common.util;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;

/**
 * 초대 링크용 공유 코드 생성기.
 *
 * 예: "aB3kX7" → https://moim.app/g/aB3kX7
 *
 * SecureRandom을 사용하는 이유:
 * - Random은 시드가 예측 가능해서 코드를 추측할 수 있음
 * - SecureRandom은 암호학적으로 안전한 난수 생성
 *
 * 6자리 영숫자 = 62^6 = 약 568억 가지 조합 → MVP 규모에서 충돌 확률 무시 가능
 */
@Slf4j
public class ShareCodeGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 6자리 랜덤 영숫자 코드 생성.
     * DB에서 중복 체크는 Service 레이어에서 수행한다.
     */
    public static String generate(){
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for(int i = 0; i < CODE_LENGTH; i++){
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        String code = sb.toString();
        log.debug("[ShareCode] 공유 코드 생성: {}", code);
        return code;    }
}
