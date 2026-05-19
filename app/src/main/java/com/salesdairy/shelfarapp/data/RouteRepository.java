package com.salesdairy.shelfarapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.salesdairy.shelfarapp.models.RouteCheckpoint;
import com.salesdairy.shelfarapp.models.RouteEdge;
import com.salesdairy.shelfarapp.utils.Constants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class RouteRepository {

    private final DBHelper dbHelper;

    public RouteRepository(Context context) {
        dbHelper = new DBHelper(context);
    }

    public enum RouteState {
        ON_ROUTE,
        UNCERTAIN,
        RECOVER_ROUTE,
        NEAR_TARGET
    }

    public static final class PathResult {
        public final List<RouteCheckpoint> path;
        public final RouteCheckpoint startCheckpoint;
        public final RouteCheckpoint targetCheckpoint;
        public final float confidence;
        public final boolean recoveryNeeded;
        public final boolean usedFallback;
        public final int nearestPathIndex;
        public final float corridorErrorMeters;
        public final RouteState routeState;

        public PathResult(List<RouteCheckpoint> path,
                          RouteCheckpoint startCheckpoint,
                          RouteCheckpoint targetCheckpoint,
                          float confidence,
                          boolean recoveryNeeded,
                          boolean usedFallback,
                          int nearestPathIndex,
                          float corridorErrorMeters,
                          RouteState routeState) {
            this.path = path == null ? Collections.<RouteCheckpoint>emptyList() : path;
            this.startCheckpoint = startCheckpoint;
            this.targetCheckpoint = targetCheckpoint;
            this.confidence = confidence;
            this.recoveryNeeded = recoveryNeeded;
            this.usedFallback = usedFallback;
            this.nearestPathIndex = Math.max(0, nearestPathIndex);
            this.corridorErrorMeters = Math.max(0f, corridorErrorMeters);
            this.routeState = routeState == null ? RouteState.UNCERTAIN : routeState;
        }

        public boolean isHighConfidence() { return confidence >= 0.60f && (routeState == RouteState.ON_ROUTE || routeState == RouteState.NEAR_TARGET); }
        public boolean shouldRecover() { return recoveryNeeded || routeState == RouteState.RECOVER_ROUTE || (confidence < 0.26f && corridorErrorMeters > 1.05f); }
        public boolean isNearTarget() { return routeState == RouteState.NEAR_TARGET; }
        public boolean isUncertain() { return routeState == RouteState.UNCERTAIN; }
    }

    public long insertCheckpoint(RouteCheckpoint checkpoint) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insert(Constants.TABLE_ROUTE_CHECKPOINTS, null, toCheckpointValues(checkpoint));
    }

    public long insertEdge(RouteEdge edge) {
        if (edge == null || edge.getFromCheckpointId() <= 0L || edge.getToCheckpointId() <= 0L
                || edge.getFromCheckpointId() == edge.getToCheckpointId()) {
            return -1L;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor existing = db.rawQuery(
                "SELECT " + Constants.COL_ID + " FROM " + Constants.TABLE_ROUTE_EDGES
                        + " WHERE " + Constants.COL_STORE_REFERENCE_ID + "=?"
                        + " AND ((" + Constants.COL_FROM_CHECKPOINT_ID + "=? AND " + Constants.COL_TO_CHECKPOINT_ID + "=?)"
                        + " OR (" + Constants.COL_FROM_CHECKPOINT_ID + "=? AND " + Constants.COL_TO_CHECKPOINT_ID + "=?)) LIMIT 1",
                new String[]{String.valueOf(edge.getStoreReferenceId()), String.valueOf(edge.getFromCheckpointId()), String.valueOf(edge.getToCheckpointId()), String.valueOf(edge.getToCheckpointId()), String.valueOf(edge.getFromCheckpointId())});
        try {
            if (existing != null && existing.moveToFirst()) {
                return existing.getLong(0);
            }
        } finally {
            if (existing != null) existing.close();
        }
        return db.insert(Constants.TABLE_ROUTE_EDGES, null, toEdgeValues(edge));
    }

    public int getNextCheckpointSequence(int storeReferenceId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COALESCE(MAX(" + Constants.COL_SEQUENCE + "), 0) FROM " + Constants.TABLE_ROUTE_CHECKPOINTS
                        + " WHERE " + Constants.COL_STORE_REFERENCE_ID + "=?",
                new String[]{String.valueOf(storeReferenceId)}
        );
        int next = 1;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    next = cursor.getInt(0) + 1;
                }
            } finally {
                cursor.close();
            }
        }
        return next;
    }

    public List<RouteCheckpoint> getCheckpointsForStoreReference(int storeReferenceId) {
        List<RouteCheckpoint> checkpoints = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_ROUTE_CHECKPOINTS, null,
                Constants.COL_STORE_REFERENCE_ID + "=?",
                new String[]{String.valueOf(storeReferenceId)}, null, null,
                Constants.COL_SEQUENCE + " ASC");
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    checkpoints.add(mapCheckpoint(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        return checkpoints;
    }


    public List<RouteCheckpoint> getCheckpointsForReference(int storeReferenceId) {
        return getCheckpointsForStoreReference(storeReferenceId);
    }

    public List<RouteEdge> getEdgesForReference(int storeReferenceId) {
        List<RouteEdge> edges = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(Constants.TABLE_ROUTE_EDGES, null,
                Constants.COL_STORE_REFERENCE_ID + "=?",
                new String[]{String.valueOf(storeReferenceId)}, null, null,
                Constants.COL_ID + " ASC");
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    edges.add(mapEdge(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        return edges;
    }

    public Map<Long, RouteCheckpoint> getCheckpointMap(int storeReferenceId) {
        List<RouteCheckpoint> checkpoints = getCheckpointsForStoreReference(storeReferenceId);
        Map<Long, RouteCheckpoint> map = new HashMap<>();
        for (RouteCheckpoint checkpoint : checkpoints) {
            map.put(checkpoint.getId(), checkpoint);
        }
        return map;
    }

    public RouteCheckpoint getNearestCheckpoint(int storeReferenceId, float x, float y, float z) {
        List<RouteCheckpoint> checkpoints = getCheckpointsForStoreReference(storeReferenceId);
        return getNearestCheckpointFromList(checkpoints, x, y, z);
    }

    public RouteCheckpoint getBestStableCheckpoint(int storeReferenceId, float x, float y, float z, String routeLabel) {
        List<RouteCheckpoint> checkpoints = getCheckpointsForStoreReference(storeReferenceId);
        RouteCheckpoint best = null;
        float bestScore = Float.MAX_VALUE;
        for (RouteCheckpoint checkpoint : checkpoints) {
            if (checkpoint == null || checkpoint.getId() <= 0L) {
                continue;
            }
            String kind = checkpoint.getKind() == null ? "" : checkpoint.getKind().trim().toUpperCase(Locale.ROOT);
            if ("SHELF".equals(kind)) {
                continue;
            }
            if (routeLabel != null && checkpoint.getRouteLabel() != null
                    && !routeLabel.equalsIgnoreCase(checkpoint.getRouteLabel())
                    && Math.abs(checkpoint.getAnchorY() - y) > 1.25f) {
                continue;
            }
            float dx = checkpoint.getAnchorX() - x;
            float dy = checkpoint.getAnchorY() - y;
            float dz = checkpoint.getAnchorZ() - z;
            float verticalWeight = Math.abs(dy) > 0.90f ? 1.10f : 0.45f;
            float distanceScore = (dx * dx) + ((dy * dy) * verticalWeight) + (dz * dz);
            float confidenceBonus = checkpoint.getCaptureConfidence() > 0f
                    ? Math.min(0.35f, checkpoint.getCaptureConfidence() * 0.28f) : 0f;
            float sceneBonus = Math.min(0.12f, Math.max(0, checkpoint.getSceneQualityScore()) * 0.04f);
            float kindBonus = ("TURN".equals(kind) || "TRANSITION".equals(kind)) ? 0.06f : 0f;
            float score = distanceScore - confidenceBonus - sceneBonus - kindBonus;
            if (score < bestScore) {
                bestScore = score;
                best = checkpoint;
            }
        }
        return best != null ? best : getNearestCheckpointFromList(checkpoints, x, y, z);
    }

    public RouteCheckpoint getNearestCheckpointFromList(List<RouteCheckpoint> checkpoints, float x, float y, float z) {
        RouteCheckpoint nearest = null;
        float bestScore = Float.MAX_VALUE;
        if (checkpoints == null) {
            return null;
        }
        for (RouteCheckpoint checkpoint : checkpoints) {
            float dx = checkpoint.getAnchorX() - x;
            float dy = checkpoint.getAnchorY() - y;
            float dz = checkpoint.getAnchorZ() - z;
            float horizontalSq = (dx * dx) + (dz * dz);
            float verticalWeight = Math.abs(dy) > 0.90f ? 1.05f : 0.45f;
            float weightedScore = horizontalSq + ((dy * dy) * verticalWeight);
            if (weightedScore < bestScore - 0.0001f) {
                bestScore = weightedScore;
                nearest = checkpoint;
            } else if (Math.abs(weightedScore - bestScore) <= 0.0001f && nearest != null
                    && checkpoint.getSequence() < nearest.getSequence()) {
                nearest = checkpoint;
            }
        }
        return nearest;
    }

    public List<RouteCheckpoint> getPathForReference(int storeReferenceId, long fromCheckpointId, long toCheckpointId) {
        Map<Long, RouteCheckpoint> checkpointMap = getCheckpointMap(storeReferenceId);
        if (checkpointMap.isEmpty() || fromCheckpointId <= 0L || toCheckpointId <= 0L) {
            return Collections.emptyList();
        }
        if (fromCheckpointId == toCheckpointId) {
            RouteCheckpoint same = checkpointMap.get(fromCheckpointId);
            if (same == null) {
                return Collections.emptyList();
            }
            List<RouteCheckpoint> single = new ArrayList<>();
            single.add(same);
            return single;
        }

        List<RouteEdge> edges = getEdgesForReference(storeReferenceId);
        Map<Long, List<EdgeStep>> graph = buildGraph(edges, checkpointMap);
        if (!graph.containsKey(fromCheckpointId) || !graph.containsKey(toCheckpointId)) {
            return fallbackSequencePath(checkpointMap, fromCheckpointId, toCheckpointId);
        }

        Map<Long, Float> distance = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        PriorityQueue<NodeCost> queue = new PriorityQueue<>(Comparator.comparingDouble(a -> a.priority));
        Set<Long> visited = new HashSet<>();

        distance.put(fromCheckpointId, 0f);
        queue.add(new NodeCost(fromCheckpointId, 0f, 0f));

        while (!queue.isEmpty()) {
            NodeCost current = queue.poll();
            if (!visited.add(current.nodeId)) {
                continue;
            }
            if (current.nodeId == toCheckpointId) {
                break;
            }
            List<EdgeStep> neighbors = graph.get(current.nodeId);
            if (neighbors == null) {
                continue;
            }
            for (EdgeStep neighbor : neighbors) {
                if (visited.contains(neighbor.toId)) {
                    continue;
                }
                float nextCost = current.cost + Math.max(0.05f, neighbor.distanceMeters);
                Float existing = distance.get(neighbor.toId);
                if (existing == null || nextCost < existing) {
                    distance.put(neighbor.toId, nextCost);
                    previous.put(neighbor.toId, current.nodeId);
                    float heuristic = heuristicToTarget(checkpointMap, neighbor.toId, toCheckpointId);
                    queue.add(new NodeCost(neighbor.toId, nextCost, nextCost + heuristic));
                }
            }
        }

        if (!previous.containsKey(toCheckpointId) && fromCheckpointId != toCheckpointId) {
            return fallbackSequencePath(checkpointMap, fromCheckpointId, toCheckpointId);
        }

        ArrayDeque<RouteCheckpoint> stack = new ArrayDeque<>();
        long node = toCheckpointId;
        RouteCheckpoint end = checkpointMap.get(node);
        if (end != null) {
            stack.push(end);
        }
        while (previous.containsKey(node)) {
            node = previous.get(node);
            RouteCheckpoint checkpoint = checkpointMap.get(node);
            if (checkpoint != null) {
                stack.push(checkpoint);
            }
        }
        return new ArrayList<>(stack);
    }

    public void deleteRouteForStoreReference(int storeReferenceId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(Constants.TABLE_ROUTE_EDGES, Constants.COL_STORE_REFERENCE_ID + "=?",
                new String[]{String.valueOf(storeReferenceId)});
        db.delete(Constants.TABLE_ROUTE_CHECKPOINTS, Constants.COL_STORE_REFERENCE_ID + "=?",
                new String[]{String.valueOf(storeReferenceId)});
    }

    private Map<Long, List<EdgeStep>> buildGraph(List<RouteEdge> edges, Map<Long, RouteCheckpoint> checkpointMap) {
        Map<Long, List<EdgeStep>> graph = new HashMap<>();
        for (Long key : checkpointMap.keySet()) {
            graph.put(key, new ArrayList<>());
        }
        if (edges != null) {
            for (RouteEdge edge : edges) {
                if (!checkpointMap.containsKey(edge.getFromCheckpointId()) || !checkpointMap.containsKey(edge.getToCheckpointId())) {
                    continue;
                }
                float distance = edge.getDistanceMeters() > 0f ? edge.getDistanceMeters() : 0.5f;
                addEdge(graph, edge.getFromCheckpointId(), edge.getToCheckpointId(), distance);
                addEdge(graph, edge.getToCheckpointId(), edge.getFromCheckpointId(), distance);
            }
        }

        List<RouteCheckpoint> ordered = new ArrayList<>(checkpointMap.values());
        Collections.sort(ordered, new Comparator<RouteCheckpoint>() {
            @Override
            public int compare(RouteCheckpoint left, RouteCheckpoint right) {
                return Integer.compare(left.getSequence(), right.getSequence());
            }
        });

        for (int i = 0; i < ordered.size() - 1; i++) {
            RouteCheckpoint a = ordered.get(i);
            RouteCheckpoint b = ordered.get(i + 1);
            float distance = distanceBetween(a, b);
            addEdge(graph, a.getId(), b.getId(), distance);
            addEdge(graph, b.getId(), a.getId(), distance);
        }

        for (int i = 0; i < ordered.size() - 2; i++) {
            RouteCheckpoint a = ordered.get(i);
            RouteCheckpoint mid = ordered.get(i + 1);
            RouteCheckpoint b = ordered.get(i + 2);
            if (!isRouteLabelCompatible(a.getRouteLabel(), b.getRouteLabel())) {
                continue;
            }
            if (Math.abs(a.getAnchorY() - b.getAnchorY()) > 0.75f) {
                continue;
            }
            float directDistance = distanceBetween(a, b);
            float viaDistance = distanceBetween(a, mid) + distanceBetween(mid, b);
            if (directDistance <= 2.25f && viaDistance > 0.05f && directDistance <= (viaDistance * 0.92f)) {
                float safeSkipWeight = Math.max(0.05f, directDistance * 1.03f);
                addEdge(graph, a.getId(), b.getId(), safeSkipWeight);
                addEdge(graph, b.getId(), a.getId(), safeSkipWeight);
            }
        }
        return graph;
    }

    private void addEdge(Map<Long, List<EdgeStep>> graph, long fromId, long toId, float distance) {
        List<EdgeStep> steps = graph.get(fromId);
        if (steps == null) {
            return;
        }
        for (int i = 0; i < steps.size(); i++) {
            EdgeStep step = steps.get(i);
            if (step.toId == toId) {
                if (distance < step.distanceMeters) {
                    steps.set(i, new EdgeStep(toId, Math.max(0.05f, distance)));
                }
                return;
            }
        }
        steps.add(new EdgeStep(toId, Math.max(0.05f, distance)));
    }

    private float distanceBetween(RouteCheckpoint a, RouteCheckpoint b) {
        float dx = a.getAnchorX() - b.getAnchorX();
        float dy = a.getAnchorY() - b.getAnchorY();
        float dz = a.getAnchorZ() - b.getAnchorZ();
        return (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }

    private List<RouteCheckpoint> fallbackSequencePath(Map<Long, RouteCheckpoint> checkpointMap,
                                                       long fromCheckpointId,
                                                       long toCheckpointId) {
        RouteCheckpoint start = checkpointMap.get(fromCheckpointId);
        RouteCheckpoint end = checkpointMap.get(toCheckpointId);
        if (start == null || end == null) {
            return Collections.emptyList();
        }
        List<RouteCheckpoint> all = new ArrayList<>(checkpointMap.values());
        Collections.sort(all, new Comparator<RouteCheckpoint>() {
            @Override
            public int compare(RouteCheckpoint left, RouteCheckpoint right) {
                return Integer.compare(left.getSequence(), right.getSequence());
            }
        });
        List<RouteCheckpoint> path = new ArrayList<>();
        int min = Math.min(start.getSequence(), end.getSequence());
        int max = Math.max(start.getSequence(), end.getSequence());
        for (RouteCheckpoint checkpoint : all) {
            if (checkpoint.getSequence() >= min && checkpoint.getSequence() <= max) {
                path.add(checkpoint);
            }
        }
        if (start.getSequence() > end.getSequence()) {
            Collections.reverse(path);
        }
        return path;
    }


    public PathResult resolveBestPathForStoreReference(int storeReferenceId, List<RouteCheckpoint> checkpoints, float x, float y, float z, long toCheckpointId) {
        Map<Long, RouteCheckpoint> checkpointMap = checkpoints == null || checkpoints.isEmpty()
                ? getCheckpointMap(storeReferenceId)
                : buildCheckpointMap(checkpoints);
        if (checkpointMap.isEmpty() || toCheckpointId <= 0L || !checkpointMap.containsKey(toCheckpointId)) {
            return new PathResult(Collections.<RouteCheckpoint>emptyList(), null, null, 0f, true, true, 0, 99f, RouteState.RECOVER_ROUTE);
        }
        RouteCheckpoint target = checkpointMap.get(toCheckpointId);
        List<RouteCheckpoint> ordered = new ArrayList<>(checkpointMap.values());
        Collections.sort(ordered, new Comparator<RouteCheckpoint>() {
            @Override public int compare(RouteCheckpoint l, RouteCheckpoint r) { return Integer.compare(l.getSequence(), r.getSequence()); }
        });

        List<RouteCheckpoint> candidates = getTopNearestCandidates(ordered, target, x, y, z, 4);
        if (candidates.isEmpty()) {
            candidates.add(target);
        }

        List<RouteCheckpoint> best = Collections.emptyList();
        RouteCheckpoint bestStart = null;
        float bestCost = Float.MAX_VALUE;
        boolean usedFallback = false;

        for (RouteCheckpoint startCandidate : candidates) {
            if (startCandidate == null) continue;
            List<RouteCheckpoint> candidatePath = getPathForReference(storeReferenceId, startCandidate.getId(), toCheckpointId);
            if (candidatePath == null || candidatePath.isEmpty()) continue;
            candidatePath = smoothPath(candidatePath);
            PathProgress progress = evaluatePathProgress(candidatePath, x, y, z);
            float entryCost = estimateEntryCost(startCandidate, x, y, z);
            float pathCost = estimatePathCost(candidatePath);
            float corridorPenalty = progress.crossTrackMeters * 0.95f;
            float reentryPenalty = progress.nearestIndex > 0 ? progress.nearestIndex * 0.05f : 0f;
            float aheadPenalty = startCandidate.getSequence() > target.getSequence()
                    ? (startCandidate.getSequence() - target.getSequence()) * 1.35f : 0f;
            float sequencePenalty = Math.abs(startCandidate.getSequence() - target.getSequence()) * 0.02f;
            float cost = entryCost + pathCost + corridorPenalty + reentryPenalty + sequencePenalty + aheadPenalty;
            if (cost < bestCost) {
                bestCost = cost;
                best = trimPathToProgress(candidatePath, progress);
                bestStart = startCandidate;
                usedFallback = candidatePath.size() <= 2 && startCandidate.getId() != toCheckpointId;
            }
        }

        if (best.isEmpty()) {
            List<RouteCheckpoint> direct = new ArrayList<>();
            direct.add(target);
            return new PathResult(direct, target, target, 0.18f, true, true, 0, 99f, RouteState.RECOVER_ROUTE);
        }

        PathProgress bestProgress = evaluatePathProgress(best, x, y, z);
        float startDistance = estimateEntryCost(bestStart != null ? bestStart : best.get(0), x, y, z);
        float confidence = computeRouteConfidence(best, startDistance, bestStart, target, bestProgress);
        RouteState routeState = determineRouteState(best, target, startDistance, bestProgress, confidence);
        boolean recoveryNeeded = routeState == RouteState.RECOVER_ROUTE;
        return new PathResult(best, bestStart, target, confidence, recoveryNeeded, usedFallback,
                bestProgress.nearestIndex, bestProgress.crossTrackMeters, routeState);
    }

    private float computeRouteConfidence(List<RouteCheckpoint> path,
                                         float startDistance,
                                         RouteCheckpoint start,
                                         RouteCheckpoint target,
                                         PathProgress progress) {
        if (path == null || path.isEmpty() || start == null || target == null || progress == null) {
            return 0f;
        }
        float confidence = 1f;
        confidence -= Math.min(0.34f, startDistance * 0.13f);
        confidence -= Math.min(0.24f, progress.crossTrackMeters * 0.18f);
        confidence -= Math.min(0.12f, Math.max(0, path.size() - 7) * 0.020f);
        confidence -= progress.usingSinglePoint ? 0.15f : 0f;
        if (Math.abs(start.getSequence() - target.getSequence()) > 10) {
            confidence -= 0.05f;
        }
        if (progress.nearestIndex > 0) {
            confidence -= Math.min(0.08f, progress.nearestIndex * 0.015f);
        }
        return Math.max(0f, Math.min(1f, confidence));
    }

    private RouteState determineRouteState(List<RouteCheckpoint> path,
                                          RouteCheckpoint target,
                                          float startDistance,
                                          PathProgress progress,
                                          float confidence) {
        if (path == null || path.isEmpty() || target == null || progress == null) {
            return RouteState.RECOVER_ROUTE;
        }
        float targetDistance = distanceToCheckpoint(target, progress.sampleX, progress.sampleY, progress.sampleZ);
        if (targetDistance <= 1.05f && progress.crossTrackMeters <= 0.55f && confidence >= 0.42f) {
            return RouteState.NEAR_TARGET;
        }
        if (progress.crossTrackMeters > 1.55f || startDistance > 2.65f) {
            return RouteState.RECOVER_ROUTE;
        }
        if (confidence < 0.40f || progress.crossTrackMeters > 0.95f) {
            return RouteState.UNCERTAIN;
        }
        return RouteState.ON_ROUTE;
    }

    private List<RouteCheckpoint> trimPathToProgress(List<RouteCheckpoint> path, PathProgress progress) {
        if (path == null || path.isEmpty() || progress == null || progress.nearestIndex <= 0 || progress.nearestIndex >= path.size()) {
            return path == null ? Collections.<RouteCheckpoint>emptyList() : path;
        }
        List<RouteCheckpoint> trimmed = new ArrayList<>();
        RouteCheckpoint anchor = path.get(progress.nearestIndex);
        if (anchor != null) {
            trimmed.add(anchor);
        }
        for (int i = progress.nearestIndex + 1; i < path.size(); i++) {
            trimmed.add(path.get(i));
        }
        return trimmed;
    }

    private PathProgress evaluatePathProgress(List<RouteCheckpoint> path, float x, float y, float z) {
        PathProgress progress = new PathProgress();
        progress.sampleX = x;
        progress.sampleY = y;
        progress.sampleZ = z;
        if (path == null || path.isEmpty()) {
            progress.crossTrackMeters = 99f;
            progress.nearestIndex = 0;
            progress.usingSinglePoint = true;
            return progress;
        }
        if (path.size() == 1) {
            progress.crossTrackMeters = estimateEntryCost(path.get(0), x, y, z);
            progress.nearestIndex = 0;
            progress.usingSinglePoint = true;
            return progress;
        }
        float best = Float.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            float d = distanceToSegment(path.get(i), path.get(i + 1), x, y, z);
            if (d < best) {
                best = d;
                bestIndex = i;
            }
        }
        progress.crossTrackMeters = best;
        progress.nearestIndex = bestIndex;
        progress.usingSinglePoint = false;
        return progress;
    }

    private float distanceToSegment(RouteCheckpoint a, RouteCheckpoint b, float x, float y, float z) {
        float ax = a.getAnchorX();
        float az = a.getAnchorZ();
        float bx = b.getAnchorX();
        float bz = b.getAnchorZ();
        float abx = bx - ax;
        float abz = bz - az;
        float apx = x - ax;
        float apz = z - az;
        float abLenSq = (abx * abx) + (abz * abz);
        float t = abLenSq <= 0.0001f ? 0f : ((apx * abx) + (apz * abz)) / abLenSq;
        t = Math.max(0f, Math.min(1f, t));
        float px = ax + (abx * t);
        float pz = az + (abz * t);
        float py = a.getAnchorY() + ((b.getAnchorY() - a.getAnchorY()) * t);
        float dx = x - px;
        float dz = z - pz;
        float dy = (y - py) * (Math.abs(y - py) > 0.90f ? 0.95f : 0.60f);
        return (float) Math.sqrt((dx * dx) + (dz * dz) + (dy * dy));
    }

    private float distanceToCheckpoint(RouteCheckpoint checkpoint, float x, float y, float z) {
        return estimateEntryCost(checkpoint, x, y, z);
    }

    private Map<Long, RouteCheckpoint> buildCheckpointMap(List<RouteCheckpoint> checkpoints) {
        Map<Long, RouteCheckpoint> map = new HashMap<>();
        if (checkpoints != null) {
            for (RouteCheckpoint checkpoint : checkpoints) {
                map.put(checkpoint.getId(), checkpoint);
            }
        }
        return map;
    }

    private List<RouteCheckpoint> getTopNearestCandidates(List<RouteCheckpoint> checkpoints,
                                                         RouteCheckpoint target,
                                                         float x,
                                                         float y,
                                                         float z,
                                                         int maxCount) {
        List<RouteCheckpoint> preferred = new ArrayList<>();
        List<RouteCheckpoint> fallback = new ArrayList<>();
        if (checkpoints == null) {
            return preferred;
        }
        int targetSequence = target != null ? target.getSequence() : Integer.MAX_VALUE;
        for (RouteCheckpoint checkpoint : checkpoints) {
            if (checkpoint == null) {
                continue;
            }
            String kind = checkpoint.getKind() == null ? "" : checkpoint.getKind().trim().toUpperCase(Locale.ROOT);
            if ("SHELF".equals(kind)) {
                continue;
            }
            boolean sameRoute = target == null || isRouteLabelCompatible(checkpoint.getRouteLabel(), target.getRouteLabel());
            boolean notTooFarAhead = target == null || checkpoint.getSequence() <= (targetSequence + 2);
            boolean nearTargetWindow = target == null || Math.abs(checkpoint.getSequence() - targetSequence) <= 6;
            if (sameRoute && notTooFarAhead) {
                preferred.add(checkpoint);
            } else if (sameRoute && nearTargetWindow) {
                fallback.add(checkpoint);
            } else if (notTooFarAhead) {
                fallback.add(checkpoint);
            }
        }
        List<RouteCheckpoint> ranked = !preferred.isEmpty() ? preferred : (!fallback.isEmpty() ? fallback : new ArrayList<>(checkpoints));
        Collections.sort(ranked, new Comparator<RouteCheckpoint>() {
            @Override public int compare(RouteCheckpoint a, RouteCheckpoint b) {
                float da = estimateCandidateScore(a, target, x, y, z);
                float db = estimateCandidateScore(b, target, x, y, z);
                int compare = Float.compare(da, db);
                if (compare != 0) {
                    return compare;
                }
                return Integer.compare(a.getSequence(), b.getSequence());
            }
        });
        if (ranked.size() > maxCount) {
            return new ArrayList<>(ranked.subList(0, maxCount));
        }
        return ranked;
    }

    private float estimateEntryCost(RouteCheckpoint checkpoint, float x, float y, float z) {
        float dx = checkpoint.getAnchorX() - x;
        float dy = checkpoint.getAnchorY() - y;
        float dz = checkpoint.getAnchorZ() - z;
        float horizontal = (float) Math.sqrt((dx * dx) + (dz * dz));
        float verticalPenalty = Math.abs(dy) > 0.90f ? 1.10f : 0.65f;
        return horizontal + (Math.abs(dy) * verticalPenalty);
    }

    private float estimateCandidateScore(RouteCheckpoint checkpoint,
                                         RouteCheckpoint target,
                                         float x,
                                         float y,
                                         float z) {
        float score = estimateEntryCost(checkpoint, x, y, z);
        if (target != null) {
            if (!isRouteLabelCompatible(checkpoint.getRouteLabel(), target.getRouteLabel())) {
                score += 1.15f;
            }
            if (checkpoint.getSequence() > target.getSequence()) {
                score += Math.min(1.6f, (checkpoint.getSequence() - target.getSequence()) * 0.55f);
            }
        }
        return score;
    }

    private boolean isRouteLabelCompatible(String left, String right) {
        if (left == null || left.trim().isEmpty() || right == null || right.trim().isEmpty()) {
            return true;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private float estimatePathCost(List<RouteCheckpoint> path) {
        if (path == null || path.size() < 2) return 0f;
        float total = 0f;
        for (int i = 1; i < path.size(); i++) {
            total += distanceBetween(path.get(i - 1), path.get(i));
        }
        return total;
    }

    private List<RouteCheckpoint> smoothPath(List<RouteCheckpoint> path) {
        if (path == null || path.size() < 3) {
            return path == null ? Collections.<RouteCheckpoint>emptyList() : path;
        }
        List<RouteCheckpoint> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));
        for (int i = 1; i < path.size() - 1; i++) {
            RouteCheckpoint prev = smoothed.get(smoothed.size() - 1);
            RouteCheckpoint curr = path.get(i);
            RouteCheckpoint next = path.get(i + 1);
            String kind = curr.getKind() == null ? "" : curr.getKind().toUpperCase(Locale.ROOT);
            if (kind.contains("TURN") || kind.contains("TRANSITION") || kind.contains("SHELF")) {
                smoothed.add(curr);
                continue;
            }
            float ax = curr.getAnchorX() - prev.getAnchorX();
            float az = curr.getAnchorZ() - prev.getAnchorZ();
            float bx = next.getAnchorX() - curr.getAnchorX();
            float bz = next.getAnchorZ() - curr.getAnchorZ();
            float amag = (float) Math.sqrt((ax * ax) + (az * az));
            float bmag = (float) Math.sqrt((bx * bx) + (bz * bz));
            if (amag < 0.05f || bmag < 0.05f) {
                smoothed.add(curr);
                continue;
            }
            float dot = ((ax / amag) * (bx / bmag)) + ((az / amag) * (bz / bmag));
            boolean nearlyStraight = dot > 0.992f;
            boolean close = distanceBetween(prev, next) < 2.6f;
            if (!(nearlyStraight && close)) {
                smoothed.add(curr);
            }
        }
        smoothed.add(path.get(path.size() - 1));
        return smoothed;
    }

    private ContentValues toCheckpointValues(RouteCheckpoint checkpoint) {
        ContentValues values = new ContentValues();
        values.put(Constants.COL_OUTLET_ID, checkpoint.getOutletId());
        values.put(Constants.COL_STORE_REFERENCE_ID, checkpoint.getStoreReferenceId());
        values.put(Constants.COL_SEQUENCE, checkpoint.getSequence());
        values.put(Constants.COL_ROUTE_LABEL, checkpoint.getRouteLabel());
        values.put(Constants.COL_CHECKPOINT_KIND, checkpoint.getKind());
        values.put(Constants.COL_ANCHOR_X, checkpoint.getAnchorX());
        values.put(Constants.COL_ANCHOR_Y, checkpoint.getAnchorY());
        values.put(Constants.COL_ANCHOR_Z, checkpoint.getAnchorZ());
        values.put(Constants.COL_YAW_DEGREES, checkpoint.getYawDegrees());
        values.put(Constants.COL_CAPTURE_CONFIDENCE, checkpoint.getCaptureConfidence());
        values.put(Constants.COL_SCENE_QUALITY_SCORE, checkpoint.getSceneQualityScore());
        values.put(Constants.COL_CREATED_AT, checkpoint.getCreatedAt());
        return values;
    }

    private ContentValues toEdgeValues(RouteEdge edge) {
        ContentValues values = new ContentValues();
        values.put(Constants.COL_STORE_REFERENCE_ID, edge.getStoreReferenceId());
        values.put(Constants.COL_FROM_CHECKPOINT_ID, edge.getFromCheckpointId());
        values.put(Constants.COL_TO_CHECKPOINT_ID, edge.getToCheckpointId());
        values.put(Constants.COL_DISTANCE_METERS, edge.getDistanceMeters());
        values.put(Constants.COL_EDGE_KIND, edge.getEdgeKind());
        values.put(Constants.COL_CREATED_AT, edge.getCreatedAt());
        return values;
    }

    private RouteCheckpoint mapCheckpoint(Cursor cursor) {
        RouteCheckpoint checkpoint = new RouteCheckpoint();
        checkpoint.setId(cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID)));
        checkpoint.setOutletId(getInt(cursor, Constants.COL_OUTLET_ID, Constants.DEFAULT_OUTLET_ID));
        checkpoint.setStoreReferenceId(getInt(cursor, Constants.COL_STORE_REFERENCE_ID, 0));
        checkpoint.setSequence(getInt(cursor, Constants.COL_SEQUENCE, 0));
        checkpoint.setRouteLabel(getString(cursor, Constants.COL_ROUTE_LABEL));
        checkpoint.setKind(getString(cursor, Constants.COL_CHECKPOINT_KIND));
        checkpoint.setAnchorX(getFloat(cursor, Constants.COL_ANCHOR_X, 0f));
        checkpoint.setAnchorY(getFloat(cursor, Constants.COL_ANCHOR_Y, 0f));
        checkpoint.setAnchorZ(getFloat(cursor, Constants.COL_ANCHOR_Z, 0f));
        checkpoint.setYawDegrees(getFloat(cursor, Constants.COL_YAW_DEGREES, 0f));
        checkpoint.setCaptureConfidence(getFloat(cursor, Constants.COL_CAPTURE_CONFIDENCE, 0f));
        checkpoint.setSceneQualityScore(getInt(cursor, Constants.COL_SCENE_QUALITY_SCORE, 0));
        checkpoint.setCreatedAt(getString(cursor, Constants.COL_CREATED_AT));
        return checkpoint;
    }

    private RouteEdge mapEdge(Cursor cursor) {
        RouteEdge edge = new RouteEdge();
        edge.setId(cursor.getLong(cursor.getColumnIndexOrThrow(Constants.COL_ID)));
        edge.setStoreReferenceId(getInt(cursor, Constants.COL_STORE_REFERENCE_ID, 0));
        edge.setFromCheckpointId(getLong(cursor, Constants.COL_FROM_CHECKPOINT_ID, 0L));
        edge.setToCheckpointId(getLong(cursor, Constants.COL_TO_CHECKPOINT_ID, 0L));
        edge.setDistanceMeters(getFloat(cursor, Constants.COL_DISTANCE_METERS, 0f));
        edge.setEdgeKind(getString(cursor, Constants.COL_EDGE_KIND));
        edge.setCreatedAt(getString(cursor, Constants.COL_CREATED_AT));
        return edge;
    }

    private String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 ? cursor.getString(index) : null;
    }

    private int getInt(Cursor cursor, String column, int def) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 ? cursor.getInt(index) : def;
    }

    private long getLong(Cursor cursor, String column, long def) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 ? cursor.getLong(index) : def;
    }

    private float getFloat(Cursor cursor, String column, float def) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 ? cursor.getFloat(index) : def;
    }

    private static final class PathProgress {
        int nearestIndex;
        float crossTrackMeters;
        boolean usingSinglePoint;
        float sampleX;
        float sampleY;
        float sampleZ;
    }

    private static final class EdgeStep {
        final long toId;
        final float distanceMeters;

        EdgeStep(long toId, float distanceMeters) {
            this.toId = toId;
            this.distanceMeters = distanceMeters;
        }
    }

    private float heuristicToTarget(Map<Long, RouteCheckpoint> checkpointMap, long fromId, long toId) {
        RouteCheckpoint from = checkpointMap.get(fromId);
        RouteCheckpoint to = checkpointMap.get(toId);
        if (from == null || to == null) {
            return 0f;
        }
        return distanceBetween(from, to) * 0.35f;
    }

    private static final class NodeCost {
        final long nodeId;
        final float cost;
        final float priority;

        NodeCost(long nodeId, float cost, float priority) {
            this.nodeId = nodeId;
            this.cost = cost;
            this.priority = priority;
        }
    }


}
