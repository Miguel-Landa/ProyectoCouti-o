package com.example.miapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:cobros-test;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
class MiappApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void registraYCargaCobroConDesgloseYEvitaDuplicados() throws Exception {
		String folio = "TEST-" + UUID.randomUUID();
		CobroRequest solicitud = new CobroRequest(
				folio,
				"Cliente de prueba",
				"Motocicleta de prueba",
				"PRUEBA1",
				"Servicio solicitado",
				"Servicio realizado",
				"Mecánico de prueba",
				new BigDecimal("450.00"),
				"Ajuste adicional",
				new BigDecimal("75.00"),
				List.of(new CobroRequest.Refaccion(
						"REF-TEST",
						"Filtro de prueba",
						new BigDecimal("100.00"),
						2
				))
		);
		String payload = objectMapper.writeValueAsString(solicitud);

		mockMvc.perform(post("/api/cobros")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.subtotal").value(725.00))
				.andExpect(jsonPath("$.iva").value(116.00))
				.andExpect(jsonPath("$.total").value(841.00))
				.andExpect(jsonPath("$.refacciones[0].importe").value(200.00));

		mockMvc.perform(get("/api/cobros"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.folio == '" + folio + "')].servicioSolicitado")
						.value(org.hamcrest.Matchers.hasItem("Servicio solicitado")));

		mockMvc.perform(post("/api/cobros")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isConflict());
	}

	@Test
	void rechazaCobroConCantidadDeRefaccionNoValida() throws Exception {
		CobroRequest solicitud = new CobroRequest(
				"TEST-INVALID-" + UUID.randomUUID(),
				"Cliente de prueba",
				"Motocicleta de prueba",
				"PRUEBA2",
				"Servicio solicitado",
				"Servicio realizado",
				"Mecánico de prueba",
				BigDecimal.ZERO,
				"",
				BigDecimal.ZERO,
				List.of(new CobroRequest.Refaccion(
						"REF-TEST",
						"Filtro de prueba",
						new BigDecimal("100.00"),
						0
				))
		);

		mockMvc.perform(post("/api/cobros")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(solicitud)))
				.andExpect(status().isBadRequest());
	}
}
