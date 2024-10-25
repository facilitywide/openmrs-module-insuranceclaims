package org.openmrs.module.insuranceclaims.web.controller;

import static org.openmrs.module.insuranceclaims.InsuranceClaimsOmodConstants.CLAIM_ALREADY_SENT_MESSAGE;
import static org.openmrs.module.insuranceclaims.InsuranceClaimsOmodConstants.CLAIM_NOT_SENT_MESSAGE;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.context.Context;
import org.openmrs.module.insuranceclaims.api.client.impl.ClaimRequestWrapper;
import org.openmrs.module.insuranceclaims.api.model.Bill;
import org.openmrs.module.insuranceclaims.api.model.InsuranceClaim;
import org.openmrs.module.insuranceclaims.api.model.InsurancePolicy;
import org.openmrs.module.insuranceclaims.api.service.InsuranceClaimService;
import org.openmrs.module.insuranceclaims.api.service.exceptions.ClaimRequestException;
import org.openmrs.module.insuranceclaims.api.service.exceptions.EligibilityRequestException;
import org.openmrs.module.insuranceclaims.api.service.fhir.FHIREligibilityService;
import org.openmrs.module.insuranceclaims.api.service.request.ExternalApiRequest;
import org.openmrs.module.insuranceclaims.forms.ClaimFormService;
import org.openmrs.module.insuranceclaims.forms.NewClaimForm;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.openmrs.module.metadatadeploy.MetadataUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import liquibase.pro.packaged.J;

// import javax.json.Json;
// import javax.json.JsonArray;
// import javax.json.JsonArrayBuilder;
// import javax.json.JsonObject;
// import javax.json.JsonObjectBuilder;

// import jakarta.json.Json;
// import jakarta.json.JsonArray;
// import jakarta.json.JsonArrayBuilder;
// import jakarta.json.JsonObject;
// import jakarta.json.JsonObjectBuilder;
// import jakarta.json.spi.JsonProvider;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.glassfish.json.JsonProviderImpl;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.CoverageEligibilityRequest;
import org.hl7.fhir.r4.model.CoverageEligibilityResponse;
import org.hl7.fhir.r4.model.CoverageEligibilityResponse.BenefitComponent;
import org.hl7.fhir.r4.model.CoverageEligibilityResponse.InsuranceComponent;
import org.hl7.fhir.r4.model.CoverageEligibilityResponse.ItemsComponent;
import org.hl7.fhir.r4.model.Money;

@RestController
@Authorized
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/insuranceclaims")
public class InsuranceClaimResourceController {

    @Autowired
    private ClaimFormService claimFormService;

    @Autowired
    private InsuranceClaimService insuranceClaimService;

    @Autowired
    private ExternalApiRequest externalApiRequest;

    @CrossOrigin(origins = "*", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
    @RequestMapping(value = "/claims", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> createClaim(@RequestBody NewClaimForm form, HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        System.out.println("Insurance Claims: REST - New Claim");

        try {
            InsuranceClaim claim = claimFormService.createClaim(form);

            // ResponseEntity<InsuranceClaim> requestResponse = new ResponseEntity<>(claim, HttpStatus.ACCEPTED);
            // return requestResponse;
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.set("Content-Type", "application/json");
            responseHeaders.setContentType(MediaType.APPLICATION_JSON);

            //Sending claim to external server
            // Even if immediate sending fails, we can send later
            try {
                externalApiRequest.sendClaimToExternalApi(claim);
            } catch (Exception ex) {
                System.err.println("Insurance Claims Error: " + ex.getMessage());
                ex.printStackTrace();
            }

            // return new ResponseEntity<String>(convertObjectToJson(claim), responseHeaders, HttpStatus.ACCEPTED);
            String responseBody = "{\n" + //
                            "    \"result\": \"SUCCESS\",\n" + //
                            "    \"message\": \"\"\n" + //
                            "}";
            return new ResponseEntity<String>(responseBody, responseHeaders, HttpStatus.ACCEPTED);
        } catch (Exception ex) {
            System.err.println("Insurance Claims Error: " + ex.getMessage());
            ex.printStackTrace();

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.set("Content-Type", "application/json");
            responseHeaders.setContentType(MediaType.APPLICATION_JSON);
            // return new ResponseEntity<String>(convertObjectToJson(claim), responseHeaders, HttpStatus.ACCEPTED);
            String responseBody = "{\"result\": \"FAILURE\",\\n" + //
                                " \"message\": \"" + ex.getMessage() + "\\n" + //
                                "}";
            return new ResponseEntity<String>(responseBody, responseHeaders, HttpStatus.BAD_REQUEST);
        }

        // return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    /**
     * Convert an object to json
     * @param obj
     * @return
     */
    public static String convertObjectToJson(Object obj) {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonResult = "";

        try {
            jsonResult = objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            System.err.println("Error serializing object: " + e.getMessage());
            e.printStackTrace();
        }

        return jsonResult;
    }

    /**
     * Create a new Bill
     * @param form
     * @param request
     * @param response
     * @return
     * @throws ResponseException
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
    @RequestMapping(value = "/bills", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody
    public ResponseEntity<Bill> createBill(@RequestBody NewClaimForm form, HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        System.out.println("Insurance Claims: REST - New Bill");
        Bill bill = claimFormService.createBill(form);

        ResponseEntity<Bill> requestResponse = new ResponseEntity<>(bill, HttpStatus.ACCEPTED);
        return requestResponse;
    }

    /**
     * Get Claim by its UUID
     * @param claimUuid
     * @param request
     * @param response
     * @return
     * @throws ResponseException
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
    @RequestMapping(value = "/claims", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity get(@RequestParam(value = "claimUuid") String claimUuid, HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        System.out.println("Insurance Claims: REST - Get Claim by UUID: " + claimUuid);
        InsuranceClaim claim = insuranceClaimService.getByUuid(claimUuid);
        ResponseEntity<InsuranceClaim> requestResponse = new ResponseEntity<>(claim, HttpStatus.ACCEPTED);
        return requestResponse;
    }

    /**
     * Checks if claim is present in external id, if external id does not have information about this
     * claim it will send it to external system, if claim was already submitted it will get update object based on external
     * information.
     * @param claimUuid uuid of claim that will be send to external server
     * @return InsuranceClaim with updated values or error message that occured during processing request
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
    @RequestMapping(value = "/claims/sendToExternal", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity sendClaimToExternalId(@RequestParam(value = "claimUuid", required = true) String claimUuid, HttpServletRequest request, HttpServletResponse response) {
        System.out.println("Insurance Claims: REST - Send Claim to external by UUID: " + claimUuid);
        InsuranceClaim claim = insuranceClaimService.getByUuid(claimUuid);

        if (claim.getExternalId() != null) {
            return ResponseEntity.badRequest().body(CLAIM_ALREADY_SENT_MESSAGE);
        }

        try {
            externalApiRequest.sendClaimToExternalApi(claim);
            return new ResponseEntity<>(claim, HttpStatus.ACCEPTED);
        } catch (ClaimRequestException requestException) {
            return new ResponseEntity<>(requestException.getMessage(), HttpStatus.EXPECTATION_FAILED);
        }
    }

    /**
     * Get a claim using its external code
     * @param claimExternalCode
     * @param request
     * @param response
     * @return
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
    @RequestMapping(value = "/claims/getFromExternal", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity getClaimFromExternalId(@RequestParam(value = "claimExternalCode") String claimExternalCode, HttpServletRequest request, HttpServletResponse response) {
        System.out.println("Insurance Claims: REST - Get Claim from external by external code: " + claimExternalCode);
        ResponseEntity requestResponse;
        try {
             ClaimRequestWrapper wrapper = externalApiRequest.getClaimFromExternalApi(claimExternalCode);
             requestResponse = new ResponseEntity<>(wrapper, HttpStatus.ACCEPTED);
        } catch (URISyntaxException wrongUrl) {
             requestResponse = new ResponseEntity<>(wrongUrl.getMessage(), HttpStatus.EXPECTATION_FAILED);
        }
        return requestResponse;
    }

    /**
     * Get a policy given its policy number
     * @param policyNumber
     * @param request
     * @param response
     * @return
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
    @RequestMapping(value = "/getPolicyFromExternal", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity getPolicyFromExternal(@RequestParam(value = "policyNumber") String policyNumber, HttpServletRequest request, HttpServletResponse response) {
        System.out.println("Insurance Claims: REST - Get Policy from external by policy number: " + policyNumber);
        ResponseEntity requestResponse;
        try {
             InsurancePolicy policy = externalApiRequest.getPatientPolicy(policyNumber);
             requestResponse = new ResponseEntity<>(policy, HttpStatus.ACCEPTED);
        } catch (EligibilityRequestException wrongUrl) {
             requestResponse = new ResponseEntity<>(wrongUrl.getMessage(), HttpStatus.EXPECTATION_FAILED);
        }
        return requestResponse;
    }

    /**
     * Uses insurance claim external api to receive ClaimResponse information and then use it to
     * to update this proper values related to this insurance claim (I.e. check if was claim was valuated, check which claim
     * items were approved).
     * @param claimUuid uuid claim which have to be updated witch external server values
     * @return InsuranceClaim with updated values
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
    @RequestMapping(value = "/claims/updateClaim", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity updateClaim(@RequestParam(value = "claimUuid") String claimUuid, HttpServletRequest request, HttpServletResponse response) {
        System.out.println("Insurance Claims: REST - Get claim update from external to check approval by UUID: " + claimUuid);
        InsuranceClaim claim = insuranceClaimService.getByUuid(claimUuid);

        if (claim.getExternalId() == null) {
            return ResponseEntity.badRequest().body(CLAIM_NOT_SENT_MESSAGE);
        }

        ResponseEntity requestResponse;
        try {
            InsuranceClaim wrapper = externalApiRequest.updateClaim(claim);
            requestResponse =  new ResponseEntity<>(wrapper, HttpStatus.ACCEPTED);
        } catch (ClaimRequestException e) {
            requestResponse =  new ResponseEntity<>(e.getMessage(), HttpStatus.EXPECTATION_FAILED);
        }

        return requestResponse;
    }

    /**
     * Get All Claims
     * @param claimUuid
     * @param request
     * @param response
     * @return
     * @throws ResponseException
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
    @RequestMapping(value = "/getallclaims", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<InsuranceClaim>> getAllClaims(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        System.out.println("Insurance Claims: REST - Get All Claims");
        List<InsuranceClaim> claims = insuranceClaimService.getAll(false);
        ResponseEntity<List<InsuranceClaim>> requestResponse = new ResponseEntity<>(claims, HttpStatus.ACCEPTED);
        return requestResponse;
    }

    /**
     * Get Claims by patient
     * @param claimUuid
     * @param request
     * @param response
     * @return
     * @throws ResponseException
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
    @RequestMapping(value = "/claimsbypatient", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<InsuranceClaim>> getClaimsByPatient(@RequestParam(value = "patientUuid") String patientUuid, HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        System.out.println("Insurance Claims: REST - Get Claim by Patient: " + patientUuid);
        List<InsuranceClaim> claims = insuranceClaimService.getAllInsuranceClaimsByPatient(patientUuid);
        ResponseEntity<List<InsuranceClaim>> requestResponse = new ResponseEntity<>(claims, HttpStatus.ACCEPTED);
        return requestResponse;
    }

    /**
     * Get Claims by Cashier Bill
     * @param claimUuid
     * @param request
     * @param response
     * @return
     * @throws ResponseException
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
    @RequestMapping(value = "/claimsbycashierbill", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<InsuranceClaim>> getClaimsByCashierBill(@RequestParam(value = "billUuid") String billUuid, HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        System.out.println("Insurance Claims: REST - Get Claim by Cashier Bill: " + billUuid);
        List<InsuranceClaim> claims = insuranceClaimService.getAllInsuranceClaimsByCashierBill(billUuid);
        ResponseEntity<List<InsuranceClaim>> requestResponse = new ResponseEntity<>(claims, HttpStatus.ACCEPTED);
        return requestResponse;
    }

    /**
     * CoverageEligibilityRequest
     *
     * Returns the eligibility request response
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.OPTIONS})
    @RequestMapping(value = "/CoverageEligibilityRequest", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody
    public ResponseEntity<JSONArray> getCoverageEligibilityRequestPOST(@RequestBody String payload, HttpServletRequest request, HttpServletResponse response) throws ResponseException {

        System.out.println("Insurance Claims: the CoverageEligibilityRequest is: " + payload);
        // Contact remote server -- TODO: we will return a payload later
        externalApiRequest.postCoverageEligibilityRequest(payload);

        //

        JSONArray coreArray = new JSONArray();
        JSONObject insuranceObject = new JSONObject();

        JSONArray jsonArray = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("packageCode", "SHA-001");
        jsonObject.put("packageName", "Eye Care");
        jsonObject.put("interventionCode", "SHA-001-01");
        jsonObject.put("interventionName", "");
        jsonObject.put("interventionTariff", 50000);
        jsonObject.put("requirePreauth", true);
        jsonObject.put("status", "Pending");

        jsonArray.add(jsonObject);

        jsonObject = new JSONObject();
        jsonObject.put("packageCode", "SHA-002");
        jsonObject.put("packageName", "Stomach Ache");
        jsonObject.put("interventionCode", "SHA-001-02");
        jsonObject.put("interventionName", "");
        jsonObject.put("interventionTariff", 70000);
        jsonObject.put("requirePreauth", false);
        jsonObject.put("status", "Pending");

        jsonArray.add(jsonObject);

        insuranceObject.put("insurer", "SHAX001");
        insuranceObject.put("inforce", true);
        insuranceObject.put("benefits", jsonArray);

        coreArray.add(insuranceObject);

        ResponseEntity<JSONArray> requestResponse = new ResponseEntity<>(coreArray, HttpStatus.ACCEPTED);
        return requestResponse;
    }

    /**
     * Get Eligibility
     * @param claimUuid
     * @param request
     * @param response
     * @return
     * @throws ResponseException
     */
    @CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
    @RequestMapping(value = "/CoverageEligibilityRequest", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity<JSONArray> getCoverageEligibilityRequestGET(@RequestParam(value = "patientUuid") String patientUuid,@RequestParam(value = "nationalId", required = false) String nationalId, HttpServletRequest request, HttpServletResponse response) throws ResponseException, IOException {


        JSONArray coreArray = new JSONArray();
        String eligibilityResponse = getCoverageStatus(nationalId);
        JSONObject insuranceObject = new JSONObject();
        insuranceObject.put("insurer", "SHAX001");
        insuranceObject.put("inforce", true);
        insuranceObject.put("start", "2024-01-01");
        insuranceObject.put("end", "2024-12-31");
        insuranceObject.put("eligibility_response", eligibilityResponse);


        coreArray.add(insuranceObject);

        ResponseEntity<JSONArray> requestResponse = new ResponseEntity<>(coreArray, HttpStatus.ACCEPTED);
        return requestResponse;
    }


    public static String getAuthToken() throws IOException {
        OkHttpClient client = new OkHttpClient();

        String auth = Context.getAdministrationService().getGlobalProperty("insuranceclaims.hiejwt.custom.encodedpass");
        String hieJwtUrl = Context.getAdministrationService().getGlobalProperty("insuranceclaims.hiejwt.url");
        Request request = new Request.Builder()
                .url(hieJwtUrl)
                .header("Authorization", "Basic " + auth)
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            System.out.println("Request failed: " + response.code() + " - " + response.message());
        }
        return response.body().string();
    }

    public static String getCoverageStatus(String nationalId) throws IOException {
        String token = getAuthToken();
        String coverageUrl = Context.getAdministrationService().getGlobalProperty("insuranceclaims.coverage.custom.url");
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Request request = new Request.Builder()
                .url(coverageUrl + nationalId)
                .addHeader("Referer", "")
                .addHeader("Authorization", "Bearer " + token)
                .build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }

}
