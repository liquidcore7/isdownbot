package bot;

import connectiontest.DownChecker;
import database.DBHandler;

import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static cfg.Configuration.BOT_TOKEN;
import static cfg.Configuration.BOT_USERNAME;

public class BotInstance extends TelegramLongPollingBot {
    private DBHandler dbConnection;
    private ExecutorService threadPool;
    private Map<String, BiConsumer<String, Long>> argRequestMapping = new HashMap<>();
    private Map<String, Consumer<Long>> noArgRequestMapping = new HashMap<>();

    public BotInstance() {
        dbConnection = new DBHandler();
        threadPool = Executors.newCachedThreadPool();

        argRequestMapping.put("/check", this::checkCommandHandler);
        argRequestMapping.put("/fullCheck", this::fullCheckCommandHandler);
        argRequestMapping.put("/addProxy", this::addProxyCommandHandler);
        argRequestMapping.put("/setTimeout", this::setTimeoutCommandHandler);

        noArgRequestMapping.put("/start", this::startCommandHandler);
        noArgRequestMapping.put("/clearProxy", this::clearProxyCommandHandler);
    }

    // TODO: logging everywhere
    private void onTelegramApiException(TelegramApiException exception, long chatId) {
        try {
            SendMessage errMessage = new SendMessage(chatId, "Command failed, try again later");
            execute(errMessage);
        } catch (TelegramApiException errFailed) {
            // nothing will help here
        }
    }

    private void runParallel(BiConsumer<String, Long> telegramCommand, String command, long userId) {
        threadPool.submit(
                (Runnable) () -> telegramCommand.accept(command, userId)
                );
    }

    private void startCommandHandler(long chatId) {
        boolean succeeded = dbConnection.addUser(chatId);
        if (succeeded) {
            SendMessage message = new SendMessage(chatId, "Welcome to isDown? bot. Now you can check any url for availability!");
            try {
                execute(message);
            } catch (TelegramApiException greetFailed) {
                onTelegramApiException(greetFailed, chatId);
            }
        }
    }

    private void clearProxyCommandHandler(long chatId) {
        boolean succeeded = dbConnection.clearUserProxy(chatId);
        SendMessage message = new SendMessage().setChatId(chatId);
        if (succeeded) {
            message.setText("Proxy successfully cleared");
        } else {
            message.setText("Clearing proxy failed, try again later");
        }
        try {
            execute(message);
        } catch (TelegramApiException clearFailed) {
            onTelegramApiException(clearFailed, chatId);
        }
    }

    private void checkCommandHandler(String url, long chatId) {
        DownChecker checker = new DownChecker(dbConnection, url, chatId);
        String message = checker.quickCheck();
        SendMessage send = new SendMessage(chatId, message);
        try {
            execute(send);
        } catch (TelegramApiException sendFailed) {
            onTelegramApiException(sendFailed, chatId);
        }
    }

    private void addProxyCommandHandler(String proxyIpPort, long chatId) {
        boolean succeeded = dbConnection.addUserProxy(chatId, proxyIpPort);
        SendMessage message = new SendMessage().setChatId(chatId);
        if (succeeded) {
            message.setText("Proxy successfully added");
        } else {
            message.setText("Adding proxy failed. Please use working HTTP proxy by specifying 'ip.ip.ip.ip:port'");
        }
        try {
            execute(message);
        } catch (TelegramApiException sendFailed) {
            onTelegramApiException(sendFailed, chatId);
        }
    }

    private void fullCheckCommandHandler(String url, long chatId) {
        DownChecker checker = new DownChecker(dbConnection, url, chatId);
        String message = checker.fullCheck();
        SendMessage sendMessage = new SendMessage(chatId, message);
        try {
            execute(sendMessage);
        } catch (TelegramApiException sendFailed) {
            onTelegramApiException(sendFailed, chatId);
        }
    }

    private void setTimeoutCommandHandler(String newTimeout, long chatId) {

        try {
            boolean succeeded = dbConnection.setCustomTimeout(Integer.parseUnsignedInt(newTimeout), chatId);
            SendMessage message = new SendMessage().setChatId(chatId);
            if (succeeded) {
                message.setText("New timeout set to " + newTimeout + " ms.");
            } else {
                message.setText("Setting new timeout failed, try again later.");
            }
            try {
                execute(message);
            } catch (TelegramApiException sendFailed) {
                onTelegramApiException(sendFailed, chatId);
            }
        } catch (NumberFormatException parseError) {
            SendMessage message = new SendMessage(chatId, "Incorrect number given");
            try {
                execute(message);
            } catch (TelegramApiException errFailed) {
                onTelegramApiException(errFailed, chatId);
            }
        }
    }

    private void argCommandRouter(String updateMessageText, long chatId) {
        SendMessage errMessage = new SendMessage().setChatId(chatId);
        String[] commandAndArgs = updateMessageText.split(" ");

        String command = commandAndArgs[0];
        String url = commandAndArgs[1].toLowerCase();

        if (!argRequestMapping.containsKey(command)) {
            errMessage.setText("No such command exists: " + command);
            try {
                execute(errMessage);
            } catch (TelegramApiException errFailed) {
                onTelegramApiException(errFailed, chatId);
            }
            return;
        }

        runParallel(argRequestMapping.get(command), url, chatId);
    }

    private void noArgCommandRouter(String updateMessageText, long chatId) {
        SendMessage errMessage = new SendMessage().setChatId(chatId);

        if (!noArgRequestMapping.containsKey(updateMessageText)) {

            if (argRequestMapping.containsKey(updateMessageText)) {
                errMessage.setText("No arguments given for command " + updateMessageText + ". Usage: /command args");
                try {
                    execute(errMessage);
                } catch (TelegramApiException errFailed) {
                    onTelegramApiException(errFailed, chatId);
                }
                return;
            }

            errMessage.setText("No such command exists: " + updateMessageText);
            try {
                execute(errMessage);
            } catch (TelegramApiException errFailed) {
                onTelegramApiException(errFailed, chatId);
            }
            return;
        }

        noArgRequestMapping.get(updateMessageText).accept(chatId);
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            String updateMessageText = update.getMessage().getText().trim();
            if (updateMessageText.startsWith("/")) {
                if (updateMessageText.contains(" ")) {
                    argCommandRouter(updateMessageText, update.getMessage().getChatId());
                } else {
                    noArgCommandRouter(updateMessageText, update.getMessage().getChatId());
                }
            } // endif isCommand
        } // endif hasMessage
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }
}
