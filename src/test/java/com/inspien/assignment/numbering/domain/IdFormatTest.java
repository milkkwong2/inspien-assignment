package com.inspien.assignment.numbering.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdFormatTest {

	@ParameterizedTest
	@CsvSource({ "1, A001", "999, A999", "1000, B001", "1998, B999", "1999, C001", "25974, Z999" })
	void formatsAndParsesBack(long sequence, String id) {
		assertThat(IdFormat.format(sequence)).isEqualTo(id);
		assertThat(IdFormat.parse(id)).contains(sequence);
	}

	@Test
	void stringOrderIsNumberOrder() {
		assertThat(IdFormat.format(999).compareTo(IdFormat.format(1000))).isNegative();
		assertThat(IdFormat.format(25973).compareTo(IdFormat.format(25974))).isNegative();
	}

	@Test
	void exhaustedAfterZ999() {
		assertThatThrownBy(() -> IdFormat.format(25975)).isInstanceOf(IdExhaustedException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "A000", "a001", "A01", "A0001", "AA01", "", "1001" })
	void rejectsValuesOutsideTheFormat(String value) {
		assertThat(IdFormat.parse(value)).isEmpty();
	}

}
