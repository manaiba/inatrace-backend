package com.abelium.inatrace.components.groupstockorder;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GroupStockOrderApiAuthTest extends AbstractMySqlIntegrationTest {

    private static final String ACME_LOT = "ACME-GROUPED-LOT-ONLY";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long acmeFacilityId;
    private Cookie ownerSession;
    private Cookie strangerSession;

    @BeforeEach
    void seed() {
        Company acme = company("Acme grouped stock");
        Company rival = company("Rival grouped stock");
        User owner = user("group-owner@acme.test");
        User stranger = user("group-stranger@rival.test");
        enroll(owner, acme);
        enroll(stranger, rival);

        Facility facility = new Facility();
        facility.setName("Acme grouped stock facility");
        facility.setCompany(acme);
        em.persist(facility);
        acmeFacilityId = facility.getId();

        StockOrder stock = new StockOrder();
        stock.setCompany(acme);
        stock.setFacility(facility);
        stock.setInternalLotNumber(ACME_LOT);
        stock.setOrderType(OrderType.PURCHASE_ORDER);
        stock.setProductionDate(LocalDate.of(2025, 7, 12));
        stock.setDeliveryTime(LocalDate.of(2025, 7, 15));
        stock.setTotalQuantity(new BigDecimal("9823.45"));
        stock.setFulfilledQuantity(BigDecimal.ZERO);
        stock.setAvailableQuantity(new BigDecimal("9823.45"));
        stock.setCreatedBy(owner);
        stock.setUpdatedBy(owner);
        em.persist(stock);

        StockOrder secondStock = new StockOrder();
        secondStock.setCompany(acme);
        secondStock.setFacility(facility);
        secondStock.setInternalLotNumber("Z-GROUPED-LOT");
        secondStock.setOrderType(OrderType.PURCHASE_ORDER);
        secondStock.setProductionDate(LocalDate.of(2025, 7, 10));
        secondStock.setDeliveryTime(LocalDate.of(2025, 7, 20));
        secondStock.setTotalQuantity(BigDecimal.TEN);
        secondStock.setFulfilledQuantity(BigDecimal.ZERO);
        secondStock.setAvailableQuantity(BigDecimal.TEN);
        secondStock.setCreatedBy(owner);
        secondStock.setUpdatedBy(owner);
        em.persist(secondStock);

        ownerSession = cookie(owner);
        strangerSession = cookie(stranger);
        em.flush();
        em.clear();
    }

    @Test
    void ownerCanReadGroupedStock() throws Exception {
        MvcResult result = request(ownerSession);
        String body = result.getResponse().getContentAsString();
        assertEquals(200, result.getResponse().getStatus(), body);
        assertTrue(body.contains(ACME_LOT), body);
    }

    @Test
    void otherTenantCannotReadGroupedStock() throws Exception {
        MvcResult result = request(strangerSession);
        String body = result.getResponse().getContentAsString();
        assertEquals(403, result.getResponse().getStatus(), body);
        assertFalse(body.contains(ACME_LOT), body);
        assertFalse(body.contains("9823.45"), body);
    }

    @Test
    void anonymousCallerCannotReadGroupedStock() throws Exception {
        MvcResult result = mockMvc.perform(get(path())).andReturn();
        String body = result.getResponse().getContentAsString();
        assertEquals(401, result.getResponse().getStatus(), body);
        assertFalse(body.contains(ACME_LOT), body);
    }

    @Test
    void supportedSortFieldsAndDirectionsRemainAvailable() throws Exception {
        assertEquals("Z-GROUPED-LOT", firstLot("date", "ASC"));
        assertEquals(ACME_LOT, firstLot("date", "DESC"));
        assertEquals(ACME_LOT, firstLot("deliveryTime", "ASC"));
        assertEquals("Z-GROUPED-LOT", firstLot("deliveryTime", "DESC"));
        assertEquals(ACME_LOT, firstLot("id", "ASC"));
        assertEquals("Z-GROUPED-LOT", firstLot("id", "DESC"));
        assertEquals(200, sortedRequest("updateTimestamp", "ASC").getResponse().getStatus());
        assertEquals(200, sortedRequest("updateTimestamp", "DESC").getResponse().getStatus());
    }

    @Test
    void unrecognizedSortExpressionsUseSafeDefault() throws Exception {
        assertEquals("Z-GROUPED-LOT", firstLot(null, "DESC"));
        assertEquals("Z-GROUPED-LOT", firstLot("date, id", "DESC"));
        assertEquals(ACME_LOT, firstLot("unknownField", "ASC"));
        assertEquals(ACME_LOT, firstLot("", "ASC"));
    }

    @Test
    void foreignCompanyCannotUseSortExpressionsToReadStock() throws Exception {
        MvcResult result = mockMvc.perform(get(path()).cookie(strangerSession)
                .param("sortBy", "date, id")).andReturn();
        assertEquals(403, result.getResponse().getStatus());
        assertFalse(result.getResponse().getContentAsString().contains(ACME_LOT));
    }

    private String firstLot(String sortBy, String direction) throws Exception {
        MvcResult result = sortedRequest(sortBy, direction);
        String body = result.getResponse().getContentAsString();
        assertEquals(200, result.getResponse().getStatus(), body);
        JsonNode root = objectMapper.readTree(body);
        JsonNode items = root.path("data").path("items");
        assertEquals(2, items.size(), body);
        return items.get(0).path("internalLotNumber").asText();
    }

    private MvcResult sortedRequest(String sortBy, String direction) throws Exception {
        var request = get(path()).cookie(ownerSession).param("sort", direction);
        if (sortBy != null) {
            request.param("sortBy", sortBy);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult request(Cookie session) throws Exception {
        return mockMvc.perform(get(path()).cookie(session)).andReturn();
    }

    private String path() {
        return "/api/chain/group-stock-order/list/facility/" + acmeFacilityId;
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);
        return company;
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setSurname("Test");
        user.setPassword("not-used-in-this-test");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        return user;
    }

    private void enroll(User user, Company company) {
        CompanyUser enrollment = new CompanyUser();
        enrollment.setUser(user);
        enrollment.setCompany(company);
        enrollment.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(enrollment);
        company.getUsers().add(enrollment);
    }

    private Cookie cookie(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }
}
