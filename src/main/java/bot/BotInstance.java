package bot;

import connectiontest.IsDownChecker;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import static cfg.Configuration.BOT_TOKEN;
import static cfg.Configuration.BOT_USERNAME;

public class BotInstance extends TelegramLongPollingBot {

    private void isDownRequestHandler(String url, long chatId) {
        IsDownChecker checker = new IsDownChecker(url);
        StringBuilder message = new StringBuilder();
        message.append("Website: ").append(url).append('\n');
        message.append("Status: ").append(
                checker.available() ? "On" : "Off")
                .append("line\n");
        SendMessage send = new SendMessage(chatId, message.toString());
        try {
            execute(send);
        } catch (TelegramApiException sendFailed) {
            try {
                SendMessage errMessage = new SendMessage(chatId, "Command failed, try again later");
                execute(errMessage);
            } catch (TelegramApiException errFailed) {
                // nothing will help here
            }
        }
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().getText().startsWith("/check")) {
                try {
                    isDownRequestHandler(
                            update.getMessage().getText().split(" ")[1],
                            update.getMessage().getChatId()
                    );
                } catch (ArrayIndexOutOfBoundsException noUrlGiven) {
                    SendMessage errMessage = new SendMessage(update.getMessage().getChatId(),
                            "Invalid syntax, try \"/command [url]\" without quotes.");
                    try {
                        execute(errMessage);
                    } catch (TelegramApiException errFailed) {
                        //
                    }

                }
            }
        }
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }
}
