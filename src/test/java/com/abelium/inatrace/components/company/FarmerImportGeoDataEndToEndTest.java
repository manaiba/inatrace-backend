package com.abelium.inatrace.components.company;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.Plot;
import com.abelium.inatrace.db.entities.common.PlotCoordinate;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.value_chain.CompanyValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end verification that a genuine farmer-import upload - through the real HTTP endpoints,
 * against a real MySQL started by Testcontainers - accepts valid plot geodata and rejects invalid
 * geodata, using the real shipped "other countries" template as the base file (the same one
 * verified column-for-column in {@link FarmerImportTemplateAssetTest}).
 *
 * <p>Every test seeds its own user, company, product type and value chain, so farmers created by
 * one test are never visible to another. The container is thrown away with the JVM, so nothing
 * needs cleaning up.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class FarmerImportGeoDataEndToEndTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final String RESOURCES = "src/test/resources/farmer-import/";

    private static final String TEMPLATE_PATH = RESOURCES + "Template_list_of_farmers_other_countries_en.xlsx";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager em;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessTokenCookieName;

    private final String runId = "geo-e2e-" + UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long companyId;
    private String accessToken;

    /** See {@code UserAuthApiTest.useAClientThatDoesNotRetryOn401} for why this is needed. */
    @BeforeEach
    void useAClientThatDoesNotRetryOn401() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @BeforeEach
    void seedFixtures() {
        tx().executeWithoutResult(status -> {
            // The template rows declare "ZZ"; the real collection files declare Cameroon, by ISO
            // code in the template and by name in the Kobo export. Seeded once per container.
            seedCountryIfAbsent("ZZ", "Geodata E2E Test Country");
            seedCountryIfAbsent("CM", "Cameroon");

            User user = new User();
            user.setEmail(runId + "@example.test");
            user.setName("Geo");
            user.setSurname("Tester");
            user.setStatus(UserStatus.ACTIVE);
            user.setLanguage(Language.EN);
            user.setRole(UserRole.SYSTEM_ADMIN);
            em.persist(user);

            Company company = new Company();
            company.setName("Geodata E2E Test Company " + runId);
            company.setStatus(CompanyStatus.ACTIVE);
            em.persist(company);

            CompanyUser companyUser = new CompanyUser();
            companyUser.setUser(user);
            companyUser.setCompany(company);
            companyUser.setRole(CompanyUserRole.COMPANY_ADMIN);
            em.persist(companyUser);

            ProductType productType = new ProductType();
            productType.setCode("GEOE2E");
            productType.setName("Geodata E2E Test Crop " + runId);
            em.persist(productType);

            ValueChain valueChain = new ValueChain();
            valueChain.setName("Geodata E2E Test Value Chain " + runId);
            valueChain.setDescription("Created by FarmerImportGeoDataEndToEndTest");
            valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
            valueChain.setCreatedBy(user);
            valueChain.setProductType(productType);
            em.persist(valueChain);

            CompanyValueChain companyValueChain = new CompanyValueChain();
            companyValueChain.setCompany(company);
            companyValueChain.setValueChain(valueChain);
            em.persist(companyValueChain);

            em.flush();

            companyId = company.getId();
            accessToken = tokenService.createAccessToken(user);
        });
    }

    private void seedCountryIfAbsent(String code, String name) {
        boolean present = !em.createQuery("SELECT c FROM Country c WHERE c.code = :code", Country.class)
                .setParameter("code", code)
                .getResultList()
                .isEmpty();
        if (!present) {
            Country country = new Country();
            country.setCode(code);
            country.setName(name);
            em.persist(country);
        }
    }

    // ---------------------------------------------------------------- Geo Data column

    @Test
    void validPolygon_isAcceptedAndPersistedWithCorrectCoordinates() throws Exception {
        String internalId = runId + "-valid";
        byte[] xlsx = buildWorkbook(internalId,
                "POLYGON((5.1717367 10.2352433, 5.1718067 10.235235, 5.1719302 10.2352027))");

        JsonNode response = callImportEndpoint(uploadDocument(xlsx));

        assertEquals(1, response.get("successful").asInt(), "expected exactly 1 farmer imported: " + response);
        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);

        tx().executeWithoutResult(status -> {
            UserCustomer farmer = farmerByInternalId(internalId);

            assertEquals(1, farmer.getPlots().size());
            Plot plot = farmer.getPlots().iterator().next();
            List<PlotCoordinate> coordinates = orderedCoordinates(plot.getCoordinates());

            // The vertices keep the cell's order. Their precision is cut to six decimals,
            // roughly 0.1 m, which is what the backend stores for every coordinate.
            assertEquals(3, coordinates.size());
            assertEquals(5.171737, coordinates.get(0).getLatitude());
            assertEquals(10.235243, coordinates.get(0).getLongitude());
            assertEquals(5.17193, coordinates.get(2).getLatitude());
            assertEquals(10.235203, coordinates.get(2).getLongitude());
            assertEquals("Plot 1", plot.getPlotName());
            assertEquals("ha", plot.getUnit());
        });
    }

    @Test
    void invalidGeodata_rejectsTheWholeRowAndCreatesNoFarmer() throws Exception {
        String internalId = runId + "-invalid";
        byte[] xlsx = buildWorkbook(internalId,
                "POLYGON((95.17 10.23, 96.18 10.24, 97.19 10.25))"); // out-of-range latitude

        JsonNode response = callImportEndpoint(uploadDocument(xlsx));

        assertEquals(0, response.get("successful").asInt(), "expected no farmers imported: " + response);
        assertEquals(1, response.get("validationErrors").size(), "expected exactly 1 row validation error: " + response);

        JsonNode rowError = response.get("validationErrors").get(0);
        JsonNode columnErrors = rowError.get("columnValidationErrors");
        assertEquals(1, columnErrors.size());
        assertEquals("INVALID_GEODATA", columnErrors.get(0).get("errorType").asText());
        assertEquals("AH6", columnErrors.get(0).get("cellAddress").asText());

        assertEquals(0L, countFarmersByInternalId(internalId), "no farmer should have been persisted for a rejected row");
    }

    @Test
    void koboGeoshape_isAcceptedAndPersisted() throws Exception {
        String internalId = runId + "-geoshape";
        // Verbatim from a real collection: "lat lon altitude accuracy", points separated by ";"
        byte[] xlsx = buildWorkbook(internalId,
                "5.1717367 10.2352433 1267.1 1.45;5.1718067 10.235235 1267.8 1.3;"
                        + "5.1719302 10.2352027 1267.0 1.3;5.1717367 10.2352433 1267.1 1.45");

        JsonNode response = callImportEndpoint(uploadDocument(xlsx));

        assertEquals(1, response.get("successful").asInt(), "expected exactly 1 farmer imported: " + response);
        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);

        tx().executeWithoutResult(status -> {
            UserCustomer farmer = farmerByInternalId(internalId);

            assertEquals(1, farmer.getPlots().size());
            Plot plot = farmer.getPlots().iterator().next();
            // 4 points listed, the last repeating the first to close the ring
            assertEquals(4, plot.getCoordinates().size());

            List<PlotCoordinate> coordinates = orderedCoordinates(plot.getCoordinates());
            assertEquals(5.171737, coordinates.get(0).getLatitude(), "altitude and accuracy are discarded");
            assertEquals(10.235243, coordinates.get(0).getLongitude());
        });
    }

    // ---------------------------------------------------------------- continuation rows

    /**
     * A real, hand-filled template, uploaded byte-for-byte as the user has it: no internal
     * ids anywhere, ODK geoshape in every geo cell, and a trailing row that carries nothing but
     * three more plots written as {@code P1(...)P2 (...)P3(...)}.
     *
     * <p>Every plot of the collection survives the hand copy, but three of them ended up against
     * the wrong farmer - which the importer cannot detect and faithfully reproduces.</p>
     */
    @Test
    void realFilledTemplate_importsEveryFarmerAndEveryPlot() throws Exception {
        byte[] xlsx = Files.readAllBytes(Paths.get(RESOURCES + "filled_template_multi_plot.xlsx"));

        JsonNode response = callImportEndpoint(uploadDocument(xlsx));

        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);
        // 7 named rows, each its own farmer despite every internal-id cell being empty.
        assertEquals(7, response.get("successful").asInt(), "farmers imported: " + response);

        tx().executeWithoutResult(status -> {
            List<UserCustomer> farmers = farmersOfTestCompany();

            assertEquals(7, farmers.size(), "one farmer per named row, not one farmer for the whole file");
            assertEquals(10, totalPlots(farmers),
                    "7 rows with one plot each, plus the 3 plots of the trailing geo-only row: "
                            + plotDistribution(farmers));

            UserCustomer tsomelou = farmer(farmers, "Bam.tsomelou");
            // 1 of its own + the 3 of the geo-only row that follows it.
            assertEquals(4, tsomelou.getPlots().size(), "the P1(...)P2 (...)P3(...) row attaches to the farmer above");
            assertEquals(4, tsomelou.getPlots().stream().map(Plot::getPlotName).distinct().count(),
                    "plots of one farmer get distinct names");

            Double size = farmer(farmers, "Gadji épouse").getPlots().iterator().next().getSize();
            // ~6685 m². In hectares that is 0.66 - the pre-fix code stored 6.68.
            assertTrue(size > 0.6 && size < 0.7, "plot size should be in hectares, was " + size);
        });
    }

    // ---------------------------------------------------------------- re-imported farmers

    private static final String FIRST_PLOT =
            "POLYGON((5.1717367 10.2352433, 5.1718067 10.235235, 5.1719302 10.2352027))";

    private static final String SECOND_PLOT =
            "POLYGON((5.2000000 10.3000000, 5.2100000 10.3100000, 5.2200000 10.3000000))";

    @Test
    void reimportedFarmerWithInternalId_getsItsNewPlotAttachedInsteadOfATwin() throws Exception {
        String internalId = runId + "-reimport";

        JsonNode first = callImportEndpoint(uploadDocument(buildWorkbook(internalId, FIRST_PLOT)));
        assertEquals(1, first.get("successful").asInt(), "first import: " + first);

        JsonNode second = callImportEndpoint(uploadDocument(buildWorkbook(internalId, SECOND_PLOT)));

        assertEquals(0, second.get("successful").asInt(), "the farmer is not created a second time: " + second);
        assertTrue(second.get("validationErrors").isEmpty(), "expected no validation errors: " + second);
        assertEquals(1, second.get("duplicates").size(), "the row is reported as a duplicate: " + second);
        assertEquals(internalId, second.get("duplicates").get(0).get("farmerCompanyInternalId").asText());

        tx().executeWithoutResult(status -> {
            assertEquals(1L, countFarmersByInternalId(internalId), "still exactly one farmer with this id");

            UserCustomer farmer = farmerByInternalId(internalId);
            assertEquals(2, farmer.getPlots().size(), "the second file's plot was attached to the existing farmer");

            List<Plot> plots = farmer.getPlots().stream()
                    .sorted((a, b) -> a.getId().compareTo(b.getId()))
                    .toList();
            List<PlotCoordinate> original = orderedCoordinates(plots.get(0).getCoordinates());
            List<PlotCoordinate> attached = orderedCoordinates(plots.get(1).getCoordinates());

            assertEquals(5.171737, original.get(0).getLatitude());
            assertEquals(10.235243, original.get(0).getLongitude());
            assertEquals(3, attached.size());
            assertEquals(5.2, attached.get(0).getLatitude());
            assertEquals(10.3, attached.get(0).getLongitude());
            assertEquals(5.22, attached.get(2).getLatitude());
            assertEquals(10.3, attached.get(2).getLongitude());
        });
    }

    @Test
    void reimportedFarmerWithoutInternalId_isOnlyReportedAsDuplicate_nothingAttached() throws Exception {
        JsonNode first = callImportEndpoint(uploadDocument(buildWorkbook(null, FIRST_PLOT)));
        assertEquals(1, first.get("successful").asInt(), "first import: " + first);

        // Same name, surname and city, so it is a duplicate - but with no internal id there is no
        // farmer the plot demonstrably belongs to.
        JsonNode second = callImportEndpoint(uploadDocument(buildWorkbook(null, SECOND_PLOT)));

        assertEquals(0, second.get("successful").asInt(), "the farmer is not created a second time: " + second);
        assertEquals(1, second.get("duplicates").size(), "the row is reported as a duplicate: " + second);

        tx().executeWithoutResult(status -> {
            List<UserCustomer> farmers = farmersOfTestCompany();
            assertEquals(1, farmers.size(), "still exactly one farmer");
            assertEquals(1, farmers.get(0).getPlots().size(), "the second file's plot was not attached");

            List<PlotCoordinate> coordinates = orderedCoordinates(farmers.get(0).getPlots().iterator().next().getCoordinates());
            assertEquals(5.171737, coordinates.get(0).getLatitude(), "the original plot is untouched");
        });
    }

    // ---------------------------------------------------------------- helpers

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    private List<UserCustomer> farmersOfTestCompany() {
        return em.createQuery(
                        "SELECT uc FROM UserCustomer uc WHERE uc.company.id = :companyId", UserCustomer.class)
                .setParameter("companyId", companyId)
                .getResultList();
    }

    private static int totalPlots(List<UserCustomer> farmers) {
        return farmers.stream().mapToInt(f -> f.getPlots().size()).sum();
    }

    private static String plotDistribution(List<UserCustomer> farmers) {
        return farmers.stream()
                .map(f -> f.getSurname() + "=" + f.getPlots().size())
                .collect(Collectors.joining(", "));
    }

    private static UserCustomer farmer(List<UserCustomer> farmers, String surname) {
        return farmers.stream()
                .filter(f -> surname.equals(f.getSurname() == null ? null : f.getSurname().trim()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no farmer " + surname + " in " + plotDistribution(farmers)));
    }

    /** Plot coordinates in the order they were written, which is the order their ids were assigned. */
    private static List<PlotCoordinate> orderedCoordinates(List<PlotCoordinate> coordinates) {
        return coordinates.stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
    }

    private UserCustomer farmerByInternalId(String internalId) {
        return em.createQuery(
                        "SELECT uc FROM UserCustomer uc WHERE uc.company.id = :companyId AND uc.farmerCompanyInternalId = :id",
                        UserCustomer.class)
                .setParameter("companyId", companyId)
                .setParameter("id", internalId)
                .getSingleResult();
    }

    private long countFarmersByInternalId(String internalId) {
        return em.createQuery(
                        "SELECT COUNT(uc) FROM UserCustomer uc WHERE uc.company.id = :companyId AND uc.farmerCompanyInternalId = :id",
                        Long.class)
                .setParameter("companyId", companyId)
                .setParameter("id", internalId)
                .getSingleResult();
    }

    /** Builds a single-data-row copy of the real shipped template, matching a realistic full farmer entry. */
    private byte[] buildWorkbook(String internalId, String geoData) throws Exception {
        try (var in = new FileInputStream(TEMPLATE_PATH);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            XSSFRow row = sheet.createRow(5); // first data row, per UserCustomerImportService.rowIndex = 5

            if (internalId != null) {
                row.createCell(0).setCellValue(internalId);
            }
            row.createCell(1).setCellValue("Tester");
            row.createCell(2).setCellValue("Geo");
            row.createCell(3).setCellValue("Test Village");
            row.createCell(4).setCellValue("Test Cell");
            row.createCell(5).setCellValue("Test Sector");
            // 6 Caserio, 7 Aldea, 8 Municipio, 9 Departamento - Honduras-only, left blank
            row.createCell(10).setCellValue("123 Test Street");
            row.createCell(11).setCellValue("Testville");
            row.createCell(12).setCellValue("Test Region");
            row.createCell(13).setCellValue("00000");
            // 14 additional address - left blank
            row.createCell(15).setCellValue("ZZ");
            row.createCell(16).setCellValue("F");
            row.createCell(17).setCellValue("555123456");
            row.createCell(18).setCellValue(runId + "@farmer.test");
            row.createCell(19).setCellValue("Y");
            row.createCell(20).setCellValue("ha");
            row.createCell(21).setCellValue(2.5);
            row.createCell(22).setCellValue(2.5);
            row.createCell(23).setCellValue(1000);
            // 24/25 second product type - left blank (template has no second product type column)
            row.createCell(26).setCellValue("N");
            // 27 area organic certified, 28 start of transition - left blank
            row.createCell(29).setCellValue("00123456789");
            row.createCell(30).setCellValue("Geo Tester");
            row.createCell(31).setCellValue("Test Bank");
            row.createCell(32).setCellValue("none");
            row.createCell(33).setCellValue(geoData);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }

    private Long uploadDocument(byte[] xlsx) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(xlsx) {
            @Override
            public String getFilename() {
                return "farmers.xlsx";
            }
        });

        ResponseEntity<String> response = rest.exchange(
                "/api/common/document?type=GENERAL", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "document upload failed: " + response.getBody());
        try {
            return objectMapper.readTree(response.getBody()).get("data").get("id").asLong();
        } catch (Exception e) {
            throw new RuntimeException("Could not parse upload response: " + response.getBody(), e);
        }
    }

    private JsonNode callImportEndpoint(Long documentId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);

        ResponseEntity<String> response = rest.exchange(
                "/api/company/userCustomers/import/farmers/" + companyId + "/" + documentId,
                HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "import call failed: " + response.getBody());
        return objectMapper.readTree(response.getBody());
    }
}
