package bot;

import connectiontest.IsDownChecker;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

public class BotInstance extends TelegramLongPollingBot {
    
    private static final String TOKEN = "546120256:AAFSS0xYUZikndLF9YEuGmXJBO5b9iAz1-k";
    private static final String BOT_USERNAME = "is_down_bot";

    private void isDownRequestHandler(String url) {
        IsDownChecker checker = new IsDownChecker(url);
    }

    @Override
    public String getBotToken() {
        return TOKEN;
    }

    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            SendMessage message = new SendMessage() // Create a SendMessage object with mandatory fields
                    .setChatId(update.getMessage().getChatId())
                    .setText(update.getMessage().getText());
            try {
                execute(message); // Call method to send the message
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    public String getBotUsername() {
        return BOT_USERNAME;
    }
}
