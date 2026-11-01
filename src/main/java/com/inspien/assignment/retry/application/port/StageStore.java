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

	/**
	 * 조건부 갱신: "사람이 확인" 표시가 있을 때만 횟수 0, 표시 해제, 다음 시도 시각 비움. 완료 시각과 결과물은 그대로다.
	 *
	 * @return 다시 시작했으면 true. 표시가 없었으면 false
	 */
	boolean restartIfFailed(String orderId, Stage stage);

}
