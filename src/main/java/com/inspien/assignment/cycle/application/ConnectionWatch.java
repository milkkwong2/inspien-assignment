package com.inspien.assignment.cycle.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.inspien.assignment.global.event.EventStage;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.event.ProcessEvents;

/**
 * 상대 시스템(FTP, 운송 DB) 연결 상태. 연결 실패는 주문의 문제가 아니라 그 주기를 건너뛰고 횟수를 세지 않는다 (D-10).
 *
 * <p>이벤트는 상태가 바뀔 때만 남긴다(시작, 복구). 하루 장애에 30초마다 쌓이면 2,880건이 된다. ERROR 로그는 매 주기
 * 남긴다 (D-22). 스케줄러 스레드 하나에서만 쓴다.
 */
public class ConnectionWatch {

	private static final Logger log = LoggerFactory.getLogger(ConnectionWatch.class);

	private final String target;

	private final EventStage stage;

	private final ProcessEvents events;

	private boolean down;

	private int skippedCycles;

	public ConnectionWatch(String target, EventStage stage, ProcessEvents events) {
		this.target = target;
		this.stage = stage;
		this.events = events;
	}

	public void failed(Exception cause) {
		log.error("{} 연결 실패: 이번 주기를 건너뜁니다 ({}회째)", this.target, this.skippedCycles + 1, cause);
		if (!this.down) {
			this.events.publishForCycle(this.stage, ProcessEvent.Result.CYCLE_SKIPPED,
					this.target + " 연결 실패, 복구될 때까지 주기를 건너뜀: " + cause.getMessage());
		}
		this.down = true;
		this.skippedCycles++;
	}

	public void connected() {
		if (this.down) {
			this.events.publishForCycle(this.stage, ProcessEvent.Result.CONNECTION_RECOVERED,
					this.target + " 연결 복구 (건너뛴 주기 " + this.skippedCycles + "회)");
			log.info("{} 연결 복구 (건너뛴 주기 {}회)", this.target, this.skippedCycles);
		}
		this.down = false;
		this.skippedCycles = 0;
	}

}
