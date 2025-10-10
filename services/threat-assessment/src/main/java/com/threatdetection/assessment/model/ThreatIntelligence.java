package com.threatdetection.assessment.model;

import jakarta.persistence.Embeddable;

/**
 * Threat intelligence information embedded in assessments
 */
@Embeddable
public class ThreatIntelligence {

    private boolean knownAttacker;
    private String campaignId;
    private int similarIncidents;
    private String threatActor;
    private String malwareFamily;
    private String cveReferences;

    // Constructors
    public ThreatIntelligence() {}

    public ThreatIntelligence(boolean knownAttacker, int similarIncidents) {
        this.knownAttacker = knownAttacker;
        this.similarIncidents = similarIncidents;
    }

    // Getters and Setters
    public boolean isKnownAttacker() { return knownAttacker; }
    public void setKnownAttacker(boolean knownAttacker) { this.knownAttacker = knownAttacker; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public int getSimilarIncidents() { return similarIncidents; }
    public void setSimilarIncidents(int similarIncidents) { this.similarIncidents = similarIncidents; }

    public String getThreatActor() { return threatActor; }
    public void setThreatActor(String threatActor) { this.threatActor = threatActor; }

    public String getMalwareFamily() { return malwareFamily; }
    public void setMalwareFamily(String malwareFamily) { this.malwareFamily = malwareFamily; }

    public String getCveReferences() { return cveReferences; }
    public void setCveReferences(String cveReferences) { this.cveReferences = cveReferences; }
}