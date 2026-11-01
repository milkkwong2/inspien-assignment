package com.inspien.assignment.inbound.application;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.inbound.domain.MessageKey;

/**
 * 메시지 키로 나눈 수신 잠금 N개 (D-16). 같은 키는 순서대로, 다른 키는 (대부분) 동시에 처리한다.
 *
 * <p>메시지 키는 완료 1분 뒤 같은 값이 새 메시지로 다시 오므로 유일 제약을 걸 수 없어, 동시 요청을 잠금으로 막는다.
 * 단일 인스턴스 전제 (D-12). 잠금은 트랜잭션 바깥에 건다. 안에서 잠그면 커밋 전에 잠금이 풀려 다음 요청이 커밋 전 상태를
 * 본다.
 */
@Component
class MessageLocks {

	private final ReentrantLock[] stripes;

	private final Duration wait;

	@Autowired
	MessageLocks(AppProperties properties) {
		this(properties.inbound().lockStripes(), properties.inbound().lockWait());
	}

	MessageLocks(int stripes, Duration wait) {
		this.stripes = new ReentrantLock[stripes];
		for (int i = 0; i < stripes; i++) {
			// 공정 잠금: 먼저 기다린 재전송이 먼저 들어간다
			this.stripes[i] = new ReentrantLock(true);
		}
		this.wait = wait;
	}

	/**
	 * @return 대기 상한 안에 얻었으면 true. 못 얻으면 503 BUSY로 재전송을 받는다
	 */
	boolean tryLock(MessageKey key) {
		try {
			return stripe(key).tryLock(this.wait.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	void unlock(MessageKey key) {
		stripe(key).unlock();
	}

	private ReentrantLock stripe(MessageKey key) {
		return this.stripes[Math.floorMod(key.value().hashCode(), this.stripes.length)];
	}

}
