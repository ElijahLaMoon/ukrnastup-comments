# Comments bot

## Notice
This project's target audience is Ukrainians. If you are one then you may be interested in [README in Ukrainian](https://github.com/ElijahLaMoon/ukrnastup-comments/blob/master/README-ukr.md)

## Overview
As of Jan. 18, 2024 Telegram doesn't offer any native means to specify a reason why some group's admin banned a user.
When you have a channel with tens or hundreds of subscribers sooner or later you'll encounter the need to moderate your comments.
With the flow of time, certain users whom you might've banned mistakenly may come back asking for an appeal, and that's where our troubles begin.
Telegram stores logs of admins' actions only for 48 hours before they're gone forever.
If your "Recent actions" are cluttered with hundreds or thousands of actions daily or if a user whom you've previously banned comes after those 48 hours you may have either extremely difficult time finding out what was the reason for a ban (to decide whether you should unban one), or it may be literally impossible.

That is exactly our case and hence the need for a custom bot which would allow us to ban people with specifying the reason, and easily look up that reason some time later. More on that in [Commands](#commands)

## Requirements
- Telegram bot from [@BotFather](https://t.me/BotFather)
- Telegram chat you'd like to moderate (see [Note](#note) below)
- Telegram channel for logs
- (for technical requirements see [Build](#build) section)

### Note
There may be some issue with [`messageGotBannedForLink`](https://github.com/ElijahLaMoon/ukrnastup-comments/blob/master/comments/src/main/scala/org/ukrnastup/comments/CommentsBot.scala#L79).
From what I could see Telegram generates links for private chats (bot is not accustomed yet for public ones) by dropping '-100' prefix of the numeric Telegram ID of the chat and a message.
If I'm not mistaken it just shrinks those IDs from 64 to 32 bit integers, so maybe there's a safer way to do it then my current method.

## How to use
Add your bot to both the channel and the chat mentioned in requirements as administator.
If you don't know Telegram IDs of those you can send a message to either or both, and then go to (I would advise in private mode of your browser) `https://api.telegram.org/botYOUR_BOT_TOKEN/getUpdates`.
In the resulting JSON you'll be able to find your IDs, most likely they are beginning with '-100'.
If your JSON is empty you either use a wrong bot, or something has already read your pending updates, just try sending a new message to the chat/channel.

Once the bot is running either [locally](#build-and-run) or on some cloud you [deployed it to](#deploy) you should be able to see the commands list in your Telegram comments chat by typing "/".
If the UI doesn't show you listed below commands then either something is wrong with your running bot, or you may need to re-open a chat or Telegram application for them to show up.

### Commands
(please note all of them may be used by admins only)
1. /ban _reason_ - this command __has to be used while replying__ to somebody you wish to ban.
_reason_ is an optional parameter which allows you to specify a ban reason
2. /lookup _@username_ - if a user was banned by this bot you may use this command to look up the reason of the ban.
It requires the user to have a username (the one that starts with a @), so if they don't have it you should ask them to make at least a temporary one
3. /update_admins - when bot starts for the first time it caches all the admins of a chat, and then uses this cache to decide whether to react to someone's command in chat or not.
When you add a new admin to the chat after the bot has already been started it won't recognize new admin unless some old admin invokes this command to update bot's cache

## Build and run
To build this bot locally you have to have installed JDK (I used JDK 17 throughout the development) and sbt.
You may obtain those by either installing [SDKMAN!](https://sdkman.io) or [Coursier](https://get-coursier.io/docs/cli-installation).
Optionally, you may install [Docker](https://docs.docker.com/get-docker) as well.

Compile and create executable binaries by running `sbt stage` in project's root.
Next, you have to create a `/data` directory where you will be running the bot executable from.
So if it's this project's root - here, if it's some Docker container - inside the container's root or inside a custom `WORKDIR`.

Then I would suggest creating a file like `./run` (don't forget to `chmod +x ./run`) in root directory of the project which will set the env variables required to run the bot.
For example (note the presence and absence of quotation marks),
```
#!/bin/sh

export BOT_TOKEN="YOUR_BOT_TOKEN_FROM_BOTFATHER" \
  COMMENTS_CHAT_ID=YOUR_CHAT_ID \
  COMMENTS_LOGS_CHANNEL_ID=YOUR_CHANNEL_ID

./comments/target/universal/stage/bin/comments
```
Your bot should now be up and react to your commands.
You may ping him by calling `/update_admins` in the chat.

Optionally, if you have Docker installed on your system, you may as well call `sbt stage;docker` (staging in required) and slightly change your running script to something like
```
#!/bin/sh

export BOT_TOKEN="YOUR_BOT_TOKEN_FROM_BOTFATHER" \
  COMMENTS_CHAT_ID=YOUR_CHAT_ID \
  COMMENTS_LOGS_CHANNEL_ID=YOUR_CHANNEL_ID

docker run org.ukrnastup/comments:latest --rm -a \
  -e BOT_TOKEN \
  -e COMMENTS_CHAT_ID \
  -e COMMENTS_LOGS_CHANNEL_ID \
```

## Deploy
This section is mostly for my own future reference.
I deploy my bot to [Fly.io](https://fly.io).
In order to do that you need to:
1. [sign up](https://fly.io/app/sign-up)
2. install the [flyctl](https://fly.io/docs/hands-on/install-flyctl)
3. create [volume](https://fly.io/docs/apps/volume-storage) (in case of the `fly.toml` config in root of this project the volume has to be named as specified in `source` of `[mounts]` section)
4. set [secrets](https://fly.io/docs/reference/secrets) like those you have in your `./run` script (I would recommend setting them with `--stage` option appended)
5. make sure you are about to deploy the last version of your code, i.e. just run `sbt stage` at this step
6. and once you run `flyctl deploy` (or `flyctl deploy --local-only` to build Docker image locally) you have to have to run as well `flyctl scale count 1`

The last command is required because Telegram allows only 1 bot instance running, and Fly.io creates 2 machines when you deploy an application for the first time.
Also, now when you'd like to test some new changes you make locally to this codebase, you have to create a separate bot and replace its token in the running script

Last thing to mention, is that `fly.toml` config creates a virtual machine with 1GB of RAM, which exceeds provided "free tier".
As of Jan. 19, 2024 you may use their VMs with only 256MB of RAM completely for free, see more on that [here](https://fly.io/docs/about/pricing/#free-allowances).
Thus, you are going to have to pay roughly $0.08 per day for this bot's VM with 3GB volume (free).
If you don't want to - maybe have a look at [GraalVM with native images](https://www.graalvm.org/latest/reference-manual/native-image), though I encountered some difficulties with making it work and gave up on it for now.

## Other
Just so I don't forget, if I ever resort to `sbt-assembly` - add this snippet to `build.sbt`
```scala
lazy val assemblySettings = Seq(
  assemblyJarName := "comments-bot.jar",
  assemblyMergeStrategy := {
    case "module-info.class" => MergeStrategy.discard
    case PathList("META-INF", xs @ _*) if xs.last == "module-info.class" =>
      MergeStrategy.discard
    case x => (assembly / assemblyMergeStrategy).value.apply(x)
  }
)
```