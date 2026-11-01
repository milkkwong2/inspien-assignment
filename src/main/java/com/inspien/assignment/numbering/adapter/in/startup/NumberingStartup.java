package com.inspien.assignment.numbering.adapter.in.startup;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import com.inspien.assignment.numbering.application.NumberingService;

/**
 * 기동 시 채번 동기화 (설계서 §7.3). 모든 빈이 만들어진 직후, 웹 서버와 스케줄러가 시작되기 전에 실행된다.
 * 실패하면 기동이 실패한다. 이미 쓴 번호를 다시 발급해 PK 충돌이 나는 것보다 뜨지 않는 편이 낫다 (D-22).
 */
@Component
class NumberingStartup implements SmartInitializingSingleton {

	private final NumberingService numbering;

	NumberingStartup(NumberingService numbering) {
		this.numbering = numbering;
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.numbering.synchronizeWithIssuedIds();
	}

}
