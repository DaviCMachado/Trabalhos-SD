package kiri.chat;

public class Main {
    public static void main(String[] args) {
        // Sem argumento, o padrão é iniciar o servidor.
        if (args.length == 0 || "server".equalsIgnoreCase(args[0])) {
            ChatServer.main(slice(args, 1));
            return;
        }

        // O modo client repassa os argumentos para o cliente interativo.
        if ("client".equalsIgnoreCase(args[0])) {
            ChatClient.main(slice(args, 1));
            return;
        }

        // Mensagem de ajuda para uso incorreto da aplicação.
        System.out.println("Uso: java -jar <app> server [port] | client <username> [host] [port]");
    }

    private static String[] slice(String[] values, int from) {
        // Extrai apenas os argumentos úteis para o modo escolhido.
        if (from >= values.length) {
            return new String[0];
        }

        String[] result = new String[values.length - from];
        System.arraycopy(values, from, result, 0, result.length);
        return result;
    }
}