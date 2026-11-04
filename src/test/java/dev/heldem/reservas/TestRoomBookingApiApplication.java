package dev.heldem.reservas;

import org.springframework.boot.SpringApplication;

public class TestRoomBookingApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(RoomBookingApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
