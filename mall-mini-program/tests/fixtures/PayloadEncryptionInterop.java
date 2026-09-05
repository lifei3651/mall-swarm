import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.service.impl.PayloadEncryptionServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Local test bridge: real production crypto, only Redis one-time storage is in-memory. */
public class PayloadEncryptionInterop {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Set<String> consumed = new HashSet<>();
        ValueOperations<String, String> values = (ValueOperations<String, String>) Proxy.newProxyInstance(
            ValueOperations.class.getClassLoader(), new Class<?>[]{ValueOperations.class}, (proxy, method, params) -> {
                if (method.getName().equals("setIfAbsent")) return consumed.add((String) params[0]);
                throw new UnsupportedOperationException("Unexpected Redis operation in fixture");
            });
        StringRedisTemplate redis = new StringRedisTemplate() {
            @Override public ValueOperations<String, String> opsForValue() { return values; }
        };
        PayloadEncryptionServiceImpl service = new PayloadEncryptionServiceImpl(redis);
        ObjectMapper json = new ObjectMapper();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = input.readLine()) != null) {
            Map<String, Object> command = json.readValue(line, Map.class);
            Object result;
            if ("issue".equals(command.get("operation"))) result = service.issueChallenge();
            else {
                Map<String, Object> body = (Map<String, Object>) command.get("body");
                try {
                    service.decryptSensitiveValues((String) command.get("challengeId"), (String) command.get("encryptedKey"), body);
                    result = Map.of("accepted", true, "body", body);
                } catch (RuntimeException rejected) {
                    // Never echo encryption material or server exception messages to the test logs.
                    result = Map.of("accepted", false);
                }
            }
            System.out.println(json.writeValueAsString(result));
            System.out.flush();
        }
    }
}
