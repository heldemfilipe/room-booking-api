package dev.heldem.reservas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RoomBookingApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
