package com.anip.kyc.dto;

import java.util.ArrayList;
import java.util.List;

public class DocumentValidationResult {
    private boolean valid = true;
    private double confidenceScore = 1.0;
    private List<String> errors = new ArrayList<>();

    public boolean isValid(){ return valid; }
    public double getConfidenceScore(){ return confidenceScore; }
    public List<String> getErrors(){ return errors; }

    public void setValid(boolean v){ this.valid = v; }
    public void setConfidenceScore(double s){ this.confidenceScore = s; }
    public void addError(String e){ this.errors.add(e); }
    public void setErrors(java.util.List<String> errors){ this.errors = errors; }
}
