import java.io.Serializable;
import java.util.Map;

public class Message implements Serializable {
    public String type;
    public Map<String, String> params;
    public Object payload;

    public Message(String type, Map<String, String> params, Object payload) {
        this.type = type;
        this.params = params;
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type='" + type + '\'' +
                ", params=" + params +
                ", payload=" + payload +
                '}';
    }
}
