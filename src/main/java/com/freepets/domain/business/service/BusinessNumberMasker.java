package com.freepets.domain.business.service;

/**
 * 사업자등록번호를 저장·표시용 형태로 가린다. {@code 1234567890} → {@code 123-45-*****}
 *
 * <p>원본은 어디에도 저장하지 않는다. 진위확인을 했다는 사실과 가린 값만 남기면 충분해서,
 * 민감정보를 들고 있을 이유가 없다.
 */
public class BusinessNumberMasker {

    private static final int BUSINESS_NUMBER_LENGTH = 10;

    private BusinessNumberMasker() {}

    public static String mask(String businessNumber) {
        if (businessNumber == null || businessNumber.length() != BUSINESS_NUMBER_LENGTH) {
            throw new IllegalArgumentException("사업자등록번호는 10자리여야 합니다.");
        }

        return businessNumber.substring(0, 3) + "-" + businessNumber.substring(3, 5) + "-*****";
    }
}
