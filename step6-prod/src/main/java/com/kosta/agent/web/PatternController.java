package com.kosta.agent.web;

import com.kosta.agent.patterns.EvaluatorOptimizer;
import com.kosta.agent.patterns.RoutingWorkflow;
import org.springframework.web.bind.annotation.*;

/**
 * 에이전트(워크플로우) 패턴 시연 엔드포인트.
 *   POST /api/patterns/route   — Routing 패턴 (문의 분류 후 전담 처리기로 분기)
 *   POST /api/patterns/refine  — Evaluator-Optimizer 패턴 (생성 → 평가 → 재생성)
 */
@RestController
@RequestMapping("/api/patterns")
public class PatternController {

    private final RoutingWorkflow routing;
    private final EvaluatorOptimizer evaluatorOptimizer;

    public PatternController(RoutingWorkflow routing, EvaluatorOptimizer evaluatorOptimizer) {
        this.routing = routing;
        this.evaluatorOptimizer = evaluatorOptimizer;
    }

    @PostMapping("/route")
    public RoutingWorkflow.RoutingResult route(@RequestBody Req req) {
        return routing.handle(req.message());
    }

    @PostMapping("/refine")
    public EvaluatorOptimizer.RefineResult refine(@RequestBody Req req) {
        return evaluatorOptimizer.refine(req.message());
    }

    public record Req(String message) {}
}
