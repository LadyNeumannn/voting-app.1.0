import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.util.*;

public class VotingClient {
    private final String host;
    private final int port;
    public VotingClient(String host, int port) { this.host = host; this.port = port; }

    public void start() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new ObjectEncoder());
                            p.addLast(new ObjectDecoder(1024 * 1024,
                                    ClassResolvers.weakCachingConcurrentResolver(null)));
                            p.addLast(new VotingClientHandler());
                        }
                    });

            ChannelFuture f = b.connect(host, port).sync();
            Channel channel = f.channel();
            System.out.println("Подключено к серверу: " + host + ":" + port);

            Scanner scanner = new Scanner(System.in);
            while (true) {
                String line = scanner.nextLine();
                if (line.equalsIgnoreCase("exit")) {
                    break;
                }

                String[] parts = line.split(" ");
                String command = parts[0];
                Map<String, String> params = new HashMap<>();

                for (String part : parts) {
                    if (part.contains("-u=")) {
                        params.put("username", part.split("=", 2)[1]);
                    } else if (part.contains("-n=")) {
                        params.put("topic", part.split("=", 2)[1]);
                    } else if (part.contains("-t=")) {
                        params.put("topic", part.split("=", 2)[1]);
                    }
                }

                switch (command) {
                    case "login" -> channel.writeAndFlush(new Message("login", params, null));
                    case "create" -> {
                        if (line.contains("create topic")) {
                            channel.writeAndFlush(new Message("create_topic", params, null));
                        } else if (line.contains("create vote")) {
                            System.out.print("Введите имя голосования: ");
                            String voteName = scanner.nextLine();
                            System.out.print("Введите описание: ");
                            String description = scanner.nextLine();
                            System.out.print("Сколько вариантов ответа? ");
                            int count = Integer.parseInt(scanner.nextLine());
                            List<String> options = new ArrayList<>();
                            for (int i = 1; i <= count; i++) {
                                System.out.print("Вариант " + i + ": ");
                                options.add(scanner.nextLine());
                            }
                            params.put("vote_name", voteName);
                            params.put("description", description);
                            channel.writeAndFlush(new Message("create_vote", params, options));
                        }
                    }
                    case "view" -> channel.writeAndFlush(new Message("view", params, null));
                    case "save" -> {
                        System.out.print("Введите имя файла для сохранения: ");
                        String filename = scanner.nextLine();
                        params.put("filename", filename);
                        channel.writeAndFlush(new Message("save", params, null));
                    }
                    case "load" -> {
                        System.out.print("Введите имя файла для загрузки: ");
                        String filename = scanner.nextLine();
                        params.put("filename", filename);
                        channel.writeAndFlush(new Message("load", params, null));
                    }
                    default -> System.out.println("Неизвестная команда");
                }
            }

            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
