import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;

import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class VotingServerHandlerTest {
    private VotingServerHandler handler;
    private ChannelHandlerContext ctx;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        handler = new VotingServerHandler();
        ctx = mock(ChannelHandlerContext.class);
        channel = new EmbeddedChannel(handler);
    }

    @Test
    void testLogin() {
        Message message = new Message("login", Map.of("username", "user1"), null);

        handler.channelRead0(ctx, message);

        assertTrue(channel.outboundMessages().contains("Пользователь \"user1\" вошел в систему"));
    }

    @Test
    void testLoginWithMissingUsername() {
        Message message = new Message("login", Map.of(), null);

        handler.channelRead0(ctx, message);

        assertTrue(channel.outboundMessages().contains("Ошибка: имя пользователя не указано"));
    }
}
@Test
void testCreateVote() {

    Message message = new Message("create_vote", Map.of("topic", "topic1", "vote_name", "vote1", "description", "vote description"), List.of("Option1", "Option2"));

    handler.channelRead0(ctx, message);

    assertTrue(channel.outboundMessages().contains("Голосование \"vote1\" успешно создано в разделе \"topic1\""));
}

@Test
void testCreateVoteWithMissingParameters() {

    Message message = new Message("create_vote", Map.of("topic", "topic1", "vote_name", "vote1"), null);

    handler.channelRead0(ctx, message);

    assertTrue(channel.outboundMessages().contains("Ошибка: недостаточно параметров для создания голосования"));
}
@Test
void testVote() {

    handler.handleCreateVote(ctx, new Message("create_vote", Map.of("topic", "topic1", "vote_name", "vote1", "description", "description"), List.of("Option1", "Option2")));


    Message message = new Message("vote", Map.of("topic", "topic1", "vote", "vote1", "option", "Option1"), null);
    handler.channelRead0(ctx, message);

    assertTrue(channel.outboundMessages().contains("Ваш голос принят: Option1"));
}

@Test
void testVoteWithAlreadyVotedUser() {

    handler.handleCreateVote(ctx, new Message("create_vote", Map.of("topic", "topic1", "vote_name", "vote1", "description", "description"), List.of("Option1", "Option2")));


    handler.handleVote(ctx, new Message("vote", Map.of("topic", "topic1", "vote", "vote1", "option", "Option1"), null));


    Message message = new Message("vote", Map.of("topic", "topic1", "vote", "vote1", "option", "Option1"), null);
    handler.channelRead0(ctx, message);

    assertTrue(channel.outboundMessages().contains("Ошибка: вы уже проголосовали в этом голосовании"));
}
@Test
void testDeleteVote() {

    handler.handleCreateVote(ctx, new Message("create_vote", Map.of("topic", "topic1", "vote_name", "vote1", "description", "description"), List.of("Option1", "Option2")));


    Message message = new Message("delete", Map.of("topic", "topic1", "vote", "vote1"), null);
    handler.channelRead0(ctx, message);

    assertTrue(channel.outboundMessages().contains("Голосование \"vote1\" успешно удалено из раздела \"topic1\""));
}

@Test
void testDeleteVoteWithNonExistentVote() {

    Message message = new Message("delete", Map.of("topic", "topic1", "vote", "vote1"), null);
    handler.channelRead0(ctx, message);

    assertTrue(channel.outboundMessages().contains("Ошибка: голосование \"vote1\" не найдено в разделе \"topic1\""));
}
