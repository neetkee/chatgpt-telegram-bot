## ChatGPT-Telegram-Bot

This bot enables you to participate in a conversation with ChatGPT via Telegram chat.

### Steps to deploy

1. Register your Telegram bot with [BotFather](https://telegram.me/BotFather) to obtain a token and a username for your bot.
2. Create an API Key for [OpenAI](https://platform.openai.com/account/api-keys).
3. Obtain your Telegram User ID to set yourself as an admin to this bot. You can do it via other bots or just run this bot with random value, your Telegram user ID will be displayed on the `start`
   command.
4. Run the Docker image with Docker Compose. See the code snippet below as an example:

```
version: "3"

services:
  chatgpt-telegram-bot:
    image: ghcr.io/neetkee/chatgpt-telegram-bot:latest
    container_name: chatgpt-telegram-bot
    restart: unless-stopped
    environment:
      - OPENAI_KEY=OpenAIKey
      - OPENAI_MODEL=gpt-4-vision-preview
      - SQLITE_PATH=/tmp/bot.sqlite
      - TELEGRAM_BOT_ADMIN_ID=1234567
      - TELEGRAM_BOT_TOKEN=123456:ABC_Def
      - TELEGRAM_BOT_USERNAME=CoolUsernameForBot
```

- You can also use [Docker Volumes](https://docs.docker.com/storage/volumes/) to manage your SQLite database.
- Note that every user who wants to use the bot needs to be granted access by an admin. As an admin, you can add users to the bot using the `add_user` command.

### Environment variables:

| Name                  | Description                                                |
|-----------------------|------------------------------------------------------------|
| OPENAI_KEY            | API key used to access OpenAI                              |
| OPENAI_MODEL          | Model to use for requests. Defaults to gpt-3.5-turbo       |
| SQLITE_PATH           | The path to the SQLite database file                       |
| TELEGRAM_BOT_ADMIN_ID | The unique identifier of the Telegram admin for the bot    |
| TELEGRAM_BOT_TOKEN    | The token string used to authenticate the Telegram bot API |
| TELEGRAM_BOT_USERNAME | The username of the Telegram bot                           |

### Commands:

| Name     | Description                                                         | Example            |
|----------|---------------------------------------------------------------------|--------------------|
| start    | Displays the user's ID and prompts to send it to the administrator. | `/start`           |
| add_user | Adds a user using their ID.                                         | `/add_user 100000` |
| cancel   | Clears the ChatGPT context.                                         | `/cancel`          |