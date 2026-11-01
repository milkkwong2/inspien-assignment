package com.inspien.assignment.inbound.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReceivedMessageRepository extends JpaRepository<ReceivedMessageEntity, String> {

	/**
	 * 같은 메시지 키의 가장 최근 기록. ID(TSID)는 시간순이다.
	 */
	Optional<ReceivedMessageEntity> findFirstByMessageKeyOrderByIdDesc(String messageKey);

}
