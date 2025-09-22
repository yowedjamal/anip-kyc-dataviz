package com.anip.kyc.validation;

import com.anip.kyc.dto.session.SessionValidationRequest;
import com.anip.kyc.dto.session.SessionValidationResponse;
import com.anip.kyc.exception.SessionValidationException;

/**
 * Utility responsible for validating session-related requests.
 * Minimal stub to satisfy controller references during compilation.
 */
public class SessionValidation {

    public static SessionValidationResponse validate(SessionValidationRequest req) throws SessionValidationException {
        // Minimal implementation: always return a basic successful response
        SessionValidationResponse resp = new SessionValidationResponse();
        resp.setSessionId(req.getSessionId());
        resp.setValid(true);
        resp.setMessage("Validated (stub)");
        return resp;
    }
}
