package com.inspien.assignment.retry.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.inspien.assignment.retry.domain.Stage;
import com.inspien.assignment.retry.domain.StageState;

/**
 * H2 ORDER_STAGE.
 */
public interface StageStore {

	Optional<StageState> find(String orderId, Stage stage);

	Map<String, StageState> findAll(Collection<String> orderIds, Stage stage);

	void save(StageState state);

}
