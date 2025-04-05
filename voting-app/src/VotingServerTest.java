import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class VotingServerHandlerTest {

    private VotingServerHandler handler;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        handler = new VotingServerHandler();
        channel = new EmbeddedChannel(handler);
    }

    private void loginAs(String username) {
        Message loginMsg = new Message("login", Map.of("username", username), null);
        channel.writeInbound(loginMsg);
        channel.readOutbound(); 
    }

    private void createVote(String topic, String voteName) {
        loginAs("user1");
        Message msg = new Message("create_vote",
                Map.of("topic", topic, "vote_name", voteName, "description", "description"),
                List.of("Option1", "Option2"));
        channel.writeInbound(msg);
        channel.readOutbound(); 
    }

    @Test
    void testLoginSuccess() {
        Message message = new Message("login", Map.of("username", "user1"), null);
        channel.writeInbound(message);

        Object response = channel.readOutbound();
        assertEquals("Пользователь \"user1\" вошел в систему", response);
    }

    @Test
    void testLoginMissingUsername() {
        Message message = new Message("login", Map.of(), null);
        channel.writeInbound(message);

        Object response = channel.readOutbound();
        assertEquals("Ошибка: имя пользователя не указано", response);
    }

    @Test
    void testCreateVoteSuccess() {
        loginAs("user1");

        Message message = new Message("create_vote",
                Map.of("topic", "topic1", "vote_name", "vote1", "description", "desc"),
                List.of("Option1", "Option2"));
        channel.writeInbound(message);

        Object response = channel.readOutbound();
        assertEquals("Голосование \"vote1\" успешно создано в разделе \"topic1\"", response);
    }

    @Test
    void testCreateVoteMissingOptions() {
        loginAs("user1");

        Message message = new Message("create_vote",
                Map.of("topic", "topic1", "vote_name", "vote1"), null);
        channel.writeInbound(message);

        Object response = channel.readOutbound();
        assertEquals("Ошибка: недостаточно параметров для создания голосования", response);
    }

    @Test
    void testVoteSuccess() {
        createVote("topic1", "vote1");

        Message voteMsg = new Message("vote",
                Map.of("topic", "topic1", "vote", "vote1", "option", "Option1"),
                null);
        channel.writeInbound(voteMsg);

        Object response = channel.readOutbound();
        assertEquals("Ваш голос принят: Option1", response);
    }

    @Test
    void testVoteAlreadyVoted() {
        createVote("topic1", "vote1");

        Message vote1 = new Message("vote",
                Map.of("topic", "topic1", "vote", "vote1", "option", "Option1"),
                null);
        channel.writeInbound(vote1);
        channel.readOutbound(); 

        Message vote2 = new Message("vote",
                Map.of("topic", "topic1", "vote", "vote1", "option", "Option1"),
                null);
        channel.writeInbound(vote2);

        Object response = channel.readOutbound();
        assertEquals("Ошибка: вы уже проголосовали в этом голосовании", response);
    }

    @Test
    void testDeleteVoteSuccess() {
        createVote("topic1", "vote1");

        Message deleteMsg = new Message("delete",
                Map.of("topic", "topic1", "vote", "vote1"), null);
        channel.writeInbound(deleteMsg);

        Object response = channel.readOutbound();
        assertEquals("Голосование \"vote1\" успешно удалено из раздела \"topic1\"", response);
    }

    @Test
    void testDeleteVoteNotFound() {
        loginAs("user1");

        Message deleteMsg = new Message("delete",
                Map.of("topic", "topic1", "vote", "vote1"), null);
        channel.writeInbound(deleteMsg);

        Object response = channel.readOutbound();
        assertEquals("Ошибка: голосование \"vote1\" не найдено в разделе \"topic1\"", response);
    }
}
