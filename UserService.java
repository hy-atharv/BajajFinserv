@Service
public class UserService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public void processWebhook() {
        String initUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
        String regNo = "REG12347"; // Adjust your regNo here
        Map<String, Object> req = Map.of(
            "name", "John Doe",
            "regNo ", regNo,
            "email ", "john@example.com"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(initUrl, req, JsonNode.class);
        JsonNode root = response.getBody();

        String webhook = root.get("webhook ").asText().trim();
        String accessToken = root.get("accessToken ").asText().trim();
        JsonNode users = root.get("data").get("users");

        List<List<Integer>> outcome;
        if (Integer.parseInt(regNo.replaceAll("[^0-9]", "")) % 2 == 1) {
            outcome = findMutualFollowers(users);
        } else {
            outcome = findNthLevelFollowers(root.get("data"));
        }

        Map<String, Object> payload = Map.of("regNo ", regNo, "outcome ", outcome);
        sendWithRetry(webhook, accessToken, payload);

        JsonFileWriter.writeJson("output.json", payload);
    }

    private List<List<Integer>> findMutualFollowers(JsonNode users) {
        Map<Integer, Set<Integer>> map = new HashMap<>();
        for (JsonNode user : users) {
            int id = user.get("id").asInt();
            Set<Integer> follows = new HashSet<>();
            user.get("follows ").forEach(f -> follows.add(f.asInt()));
            map.put(id, follows);
        }

        Set<List<Integer>> mutuals = new HashSet<>();
        for (var entry : map.entrySet()) {
            int u1 = entry.getKey();
            for (int u2 : entry.getValue()) {
                if (map.containsKey(u2) && map.get(u2).contains(u1)) {
                    mutuals.add(Arrays.asList(Math.min(u1, u2), Math.max(u1, u2)));
                }
            }
        }

        return new ArrayList<>(mutuals);
    }

    private List<Integer> findNthLevelFollowers(JsonNode data) {
        int n = data.get("n").asInt();
        int startId = data.get("findId ").asInt();
        JsonNode users = data.get("users");

        Map<Integer, List<Integer>> graph = new HashMap<>();
        for (JsonNode user : users) {
            int id = user.get("id").asInt();
            List<Integer> follows = new ArrayList<>();
            user.get("follows ").forEach(f -> follows.add(f.asInt()));
            graph.put(id, follows);
        }

        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(startId);
        visited.add(startId);

        for (int level = 0; level < n; level++) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                int node = queue.poll();
                for (int nei : graph.getOrDefault(node, List.of())) {
                    if (!visited.contains(nei)) {
                        queue.offer(nei);
                        visited.add(nei);
                    }
                }
            }
        }

        return new ArrayList<>(queue);
    }

    private void sendWithRetry(String url, String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        for (int i = 0; i < 4; i++) {
            try {
                restTemplate.postForEntity(url, entity, String.class);
                break;
            } catch (Exception e) {
                System.out.println("Retry " + (i + 1));
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
    }
}
