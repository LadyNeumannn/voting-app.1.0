public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("server")) {
            new VotingServer(8080).start();
        } else {
            new VotingClient("localhost", 8080).start();
        }
    }
}