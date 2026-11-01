package com.inspien.assignment.inbound.adapter.out.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface MessageOrderRepository extends JpaRepository<MessageOrderEntity, String> {

	List<MessageOrderEntity> findByMessageIdOrderBySeqAsc(String messageId);

	List<MessageOrderEntity> findByOrderIdIn(Collection<String> orderIds);

}
