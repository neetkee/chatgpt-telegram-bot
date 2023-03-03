## ChatGPT-Telegram-Bot

### Environment variables:

| Name                  | Description                                                |
|-----------------------|------------------------------------------------------------|
| OPENAI_KEY            | API key used to access OpenAI                              |
| SQLITE_PATH           | The path to the SQLite database file                       |
| TELEGRAM_BOT_ADMIN_ID | The unique identifier of the Telegram admin for the bot    |
| TELEGRAM_BOT_TOKEN    | The token string used to authenticate the Telegram bot API |
| TELEGRAM_BOT_USERNAME | The username of the Telegram bot                           |

### Commands:

| Name     | Description                                                         | Example         |
|----------|---------------------------------------------------------------------|-----------------|
| start    | Displays the user's ID and prompts to send it to the administrator. | `/start`        |
| add_user | Adds a user using their ID.                                         | `/add_user 123` |
| cancel   | Clears the ChatGPT context.                                         | `/cancel`       |