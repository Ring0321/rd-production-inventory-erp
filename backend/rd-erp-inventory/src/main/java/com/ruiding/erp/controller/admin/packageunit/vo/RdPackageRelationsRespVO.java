package com.ruiding.erp.controller.admin.packageunit.vo;

import lombok.Data;

import java.util.List;

@Data
public class RdPackageRelationsRespVO {
    private String rootPackageCode;
    private String rootNodeKey;
    private String scope;
    private List<Node> nodes;
    private List<Edge> edges;
    private List<Section> sections;

    @Data
    public static class Node {
        private String nodeKey;
        private String kind;
        private String label;
        private String status;
        private Long referenceId;
        private String packageCode;
        private Long locationId;
        private String locationCode;
        private String description;
    }

    @Data
    public static class Edge {
        private String edgeKey;
        private String fromNodeKey;
        private String toNodeKey;
        private String relation;
        private String label;
        private String evidenceType;
        private Long evidenceId;
        private Boolean sampled;
        private String lineStatus;
        private Integer sourceVersion;
    }

    @Data
    public static class Section {
        private String kind;
        private String state;
        // Emitted relations in this domain; GRAPH counts emitted nodes. Never a hidden total.
        private int returnedCount;
        private String reason;
    }
}
