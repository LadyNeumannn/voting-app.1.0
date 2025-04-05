import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.*;
import java.util.logging.Logger;

public class VotingServerHandler extends SimpleChannelInboundHandler<Object> {
    private static final Map<ChannelHandlerContext, String> userSessions = new HashMap<>();
    private static final Set<String> loggedInUsers = new HashSet<>();
    private static final Map<String, List<String>> topics = new HashMap<>();
    private static final Map<String, Map<String, Integer>> votesMap = new HashMap<>(); // Голоса для голосования
    private static final Logger logger = LoggerUtil.getLogger(VotingServerHandler.class.getName());

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Message message)) {
            ctx.writeAndFlush("Ошибка: сообщение должно быть типа Message");
            return;
        }

        switch (message.type) {
            case "login" -> handleLogin(ctx, message);
            case "create_topic" -> handleCreateTopic(ctx, message);
            case "view" -> handleViewTopics(ctx, message);
            case "create_vote" -> handleCreateVote(ctx, message);
            case "vote" -> handleVote(ctx, message);
            case "view_vote" -> handleViewVote(ctx, message);
            case "delete" -> handleDeleteVote(ctx, message);
            case "load" -> handleLoad(ctx, message);
            case "save" -> handleSave(ctx, message);
            default -> ctx.writeAndFlush("Неизвестная команда: " + message.type);
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, Message message) {
        String username = message.params.get("username");
        if (username == null || username.isBlank()) {
            ctx.writeAndFlush("Ошибка: имя пользователя не указано");
            return;
        }
        userSessions.put(ctx, username);
        loggedInUsers.add(username);
        logger.info("Пользователь вошел: " + username);
        ctx.writeAndFlush("Пользователь \"" + username + "\" вошел в систему");
    }

    private void handleCreateTopic(ChannelHandlerContext ctx, Message message) {
        if (!userSessions.containsKey(ctx)) {
            ctx.writeAndFlush("Ошибка: необходимо выполнить login");
            return;
        }
        String topicName = message.params.get("topic");
        if (topicName == null || topicName.isBlank()) {
            ctx.writeAndFlush("Ошибка: имя раздела не указано");
            return;
        }
        synchronized (topics) {
            if (topics.containsKey(topicName)) {
                ctx.writeAndFlush("Ошибка: раздел с таким именем уже существует");
            } else {
                topics.put(topicName, new ArrayList<>());
                logger.info("Создан новый раздел: " + topicName);
                ctx.writeAndFlush("Раздел \"" + topicName + "\" успешно создан");
            }
        }
    }

    private void handleViewTopics(ChannelHandlerContext ctx, Message message) {
        if (!userSessions.containsKey(ctx)) {
            ctx.writeAndFlush("Ошибка: необходимо выполнить login");
            return;
        }

        String topicFilter = message.params.get("topic");
        if (topicFilter != null) {
            List<String> votesInTopic = topics.get(topicFilter);
            if (votesInTopic == null) {
                ctx.writeAndFlush("Ошибка: раздел \"" + topicFilter + "\" не найден");
            } else {
                StringBuilder sb = new StringBuilder("Голосования в разделе \"" + topicFilter + "\":\n");
                for (String vote : votesInTopic) {
                    sb.append("- ").append(vote).append("\n");
                }
                logger.info("Просмотр голосований в разделе: " + topicFilter);
                ctx.writeAndFlush(sb.toString());
            }
        } else {
            StringBuilder sb = new StringBuilder("Список разделов:\n");
            for (var entry : topics.entrySet()) {
                sb.append("- ").append(entry.getKey())
                        .append(" (votes in topic=").append(entry.getValue().size()).append(")\n");
            }
            logger.info("Просмотр всех разделов");
            ctx.writeAndFlush(sb.toString());
        }
    }

    private void handleCreateVote(ChannelHandlerContext ctx, Message message) {
        if (!userSessions.containsKey(ctx)) {
            ctx.writeAndFlush("Ошибка: необходимо выполнить login");
            return;
        }

        String topic = message.params.get("topic");
        String voteName = message.params.get("vote_name");
        String description = message.params.get("description");
        Object payload = message.payload;

        if (topic == null || voteName == null || description == null || payload == null) {
            ctx.writeAndFlush("Ошибка: недостаточно параметров для создания голосования");
            return;
        }

        if (!(payload instanceof List options)) {
            ctx.writeAndFlush("Ошибка: ожидается список вариантов ответа");
            return;
        }

        synchronized (topics) {
            if (!topics.containsKey(topic)) {
                ctx.writeAndFlush("Ошибка: раздел \"" + topic + "\" не найден");
                return;
            }

            if (votesMap.containsKey(voteName)) {
                ctx.writeAndFlush("Ошибка: голосование с таким именем уже существует");
                return;
            }

            topics.get(topic).add(voteName);
            votesMap.put(voteName, new HashMap<>());
            logger.info("Создано новое голосование \"" + voteName + "\" в разделе \"" + topic + "\"");
            ctx.writeAndFlush("Голосование \"" + voteName + "\" успешно создано в разделе \"" + topic + "\"");
        }
    }

  
    private void handleVote(ChannelHandlerContext ctx, Message message) {
        if (!userSessions.containsKey(ctx)) {
            ctx.writeAndFlush("Ошибка: необходимо выполнить login");
            return;
        }

        String topic = message.params.get("topic");
        String voteName = message.params.get("vote");
        String username = userSessions.get(ctx);

        if (topic == null || voteName == null) {
            ctx.writeAndFlush("Ошибка: недостаточно параметров для голосования");
            return;
        }

        synchronized (topics) {
            if (!topics.containsKey(topic)) {
                ctx.writeAndFlush("Ошибка: раздел \"" + topic + "\" не найден");
                return;
            }

            if (!topics.get(topic).contains(voteName)) {
                ctx.writeAndFlush("Ошибка: голосование \"" + voteName + "\" не найдено в разделе \"" + topic + "\"");
                return;
            }

            Map<String, Integer> votesForVote = votesMap.get(voteName);
            if (votesForVote.containsKey(username)) {
                ctx.writeAndFlush("Ошибка: вы уже проголосовали в этом голосовании");
                return;
            }

            String chosenOption = message.params.get("option");
            if (chosenOption == null) {
                ctx.writeAndFlush("Ошибка: необходимо выбрать вариант ответа");
                return;
            }

            votesForVote.put(chosenOption, votesForVote.getOrDefault(chosenOption, 0) + 1);
            ctx.writeAndFlush("Ваш голос принят: " + chosenOption);
        }
    }


    private void handleViewVote(ChannelHandlerContext ctx, Message message) {
        if (!userSessions.containsKey(ctx)) {
            ctx.writeAndFlush("Ошибка: необходимо выполнить login");
            return;
        }

        String topic = message.params.get("topic");
        String voteName = message.params.get("vote");

        if (topic == null || voteName == null) {
            ctx.writeAndFlush("Ошибка: недостаточно параметров для просмотра голосования");
            return;
        }

        synchronized (topics) {
            if (!topics.containsKey(topic)) {
                ctx.writeAndFlush("Ошибка: раздел \"" + topic + "\" не найден");
                return;
            }

            if (!topics.get(topic).contains(voteName)) {
                ctx.writeAndFlush("Ошибка: голосование \"" + voteName + "\" не найдено в разделе \"" + topic + "\"");
                return;
            }

            Map<String, Integer> votesForVote = votesMap.get(voteName);
            if (votesForVote == null || votesForVote.isEmpty()) {
                ctx.writeAndFlush("Нет голосов для голосования \"" + voteName + "\"");
                return;
            }

            StringBuilder sb = new StringBuilder("Результаты голосования \"" + voteName + "\" в разделе \"" + topic + "\":\n");
            for (String option : votesForVote.keySet()) {
                sb.append(option).append(": ").append(votesForVote.get(option)).append(" голосов\n");
            }
            ctx.writeAndFlush(sb.toString());
        }
    }


    private void handleDeleteVote(ChannelHandlerContext ctx, Message message) {
        if (!userSessions.containsKey(ctx)) {
            ctx.writeAndFlush("Ошибка: необходимо выполнить login");
            return;
        }

        String topic = message.params.get("topic");
        String voteName = message.params.get("vote");

        if (topic == null || voteName == null) {
            ctx.writeAndFlush("Ошибка: недостаточно параметров для удаления голосования");
            return;
        }

        synchronized (topics) {
            if (!topics.containsKey(topic)) {
                ctx.writeAndFlush("Ошибка: раздел \"" + topic + "\" не найден");
                return;
            }

            if (!topics.get(topic).contains(voteName)) {
                ctx.writeAndFlush("Ошибка: голосование \"" + voteName + "\" не найдено в разделе \"" + topic + "\"");
                return;
            }


            topics.get(topic).remove(voteName);
            votesMap.remove(voteName);
            ctx.writeAndFlush("Голосование \"" + voteName + "\" успешно удалено из раздела \"" + topic + "\"");
        }
    }
}
private void handleSave(ChannelHandlerContext ctx, Message message) {
    String filename = message.params.get("filename");
    if (filename == null || filename.isBlank()) {
        ctx.writeAndFlush("Ошибка: имя файла не указано");
        return;
    }

    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
        out.writeObject(topics);
        out.writeObject(votes);
        ctx.writeAndFlush("Данные успешно сохранены в файл: " + filename);
        logger.info("Данные сохранены в файл: " + filename);
    } catch (IOException e) {
        ctx.writeAndFlush("Ошибка при сохранении данных: " + e.getMessage());
        logger.severe("Ошибка при сохранении данных: " + e.getMessage());
    }
}

private void handleLoad(ChannelHandlerContext ctx, Message message) {
    String filename = message.params.get("filename");
    if (filename == null || filename.isBlank()) {
        ctx.writeAndFlush("Ошибка: имя файла не указано");
        return;
    }

    try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))) {
        topics.clear();
        votes.clear();
        topics.putAll((Map<String, List<String>>) in.readObject());
        votes.putAll((Map<String, List<String>>) in.readObject());
        ctx.writeAndFlush("Данные успешно загружены из файла: " + filename);
        logger.info("Данные загружены из файла: " + filename);
    } catch (IOException | ClassNotFoundException e) {
        ctx.writeAndFlush("Ошибка при загрузке данных: " + e.getMessage());
        logger.severe("Ошибка при загрузке данных: " + e.getMessage());
    }
}
