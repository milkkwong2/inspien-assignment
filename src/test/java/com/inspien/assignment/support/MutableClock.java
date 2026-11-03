package com.inspien.assignment.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 테스트에서 시간을 앞으로 보낼 수 있는 시계. {@link #reset()}으로 처음 시각에 돌아간다.
 */
public class MutableClock extends Clock {

	private final Instant start;

	private volatile Instant now;

	private final ZoneId zone;

	public MutableClock(Instant start, ZoneId zone) {
		this.start = start;
		this.now = start;
		this.zone = zone;
	}

	public void advance(Duration duration) {
		this.now = this.now.plus(duration);
	}

	public void reset() {
		this.now = this.start;
	}

	@Override
	public ZoneId getZone() {
		return this.zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(this.now, zone);
	}

	@Override
	public Instant instant() {
		return this.now;
	}

}
