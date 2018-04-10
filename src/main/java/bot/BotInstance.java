package bot;

import connectiontest.DownChecker;
import database.DBHandler;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static cfg.Configuration.BOT_TOKEN;
import static cfg.Configuration.BOT_USERNAME;

public class BotInstance extends TelegramLongPollingBot {
    private DBHandler dbConnection;

    private Map<String, BiConsumer<String, Long>> requestMapping = new HashMap<>();

    public BotInstance() {
        dbConnection = new DBHandler();

        requestMapping.put("/check", this::checkCommandHandler);
        requestMapping.put("/fullCheck", this::fullCheckCommandHandler);
        requestMapping.put("/addProxy", this::addProxyCommandHandler);
        requestMapping.put("/setTimeout", this::setTimeoutCommandHandler);
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
                message.setText("New timeout set to " + newTimeout + " milliseconds.");
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

    private void commandRouter(String updateMessageText, long chatId) {
        SendMessage errMessage = new SendMessage().setChatId(chatId);
        String[] commandAndArgs = updateMessageText.split(" ");

        if (commandAndArgs.length < 2) {
            errMessage.setText("No arguments given for command " + commandAndArgs[0]);
            try {
                execute(errMessage);
            } catch (TelegramApiException errFailed) {
                onTelegramApiException(errFailed, chatId);
            }
            return;
        }

        if (!requestMapping.containsKey(commandAndArgs[0])) {
            errMessage.setText("No such command exists: " + commandAndArgs[0]);
            try {
                execute(errMessage);
            } catch (TelegramApiException errFailed) {
                onTelegramApiException(errFailed, chatId);
            }
            return;
        }

        requestMapping.get(commandAndArgs[0])
                .accept(commandAndArgs[1], chatId);
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().getText().startsWith("/")) {
            if (update.getMessage().getText().equals("/start")) {
                startCommandHandler(update.getMessage().getChatId());
            } else {
                commandRouter(update.getMessage().getText(), update.getMessage().getChatId());
            }
        }
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }
}
