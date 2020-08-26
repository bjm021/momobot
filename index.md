## A SIMPLE DISCORD MUSIC BOT!

YouTube, Vimeo, Twitch, Bandcamp, Soundcloud Equalizer, BassBoost

## Installation

Please visit our [wiki](https://github.com/bjm021/momobot/wiki) for more information!

## Commands

### Generic Commands

#### -now

| usage | description |
|-----|-----|
| -now \<identifier\> | Plays a resource immediately |

#### -add

| usage | description |
|-----|-----|
| -add \<identifier\> | Adds a resource to the queue |

#### -pause

| usage | description |
|-----|-----|
| -pause | Pauses the current playback |

#### -resume

| usage | description |
|-----|-----|
| -resume | Resumes the playback |

#### -volume

| usage | description |
|-----|-----|
| -volume \<value\> | Changes the volume of the bot |

#### -clear

| usage | description |
|-----|-----|
| -clear | Clears the queue completely |

#### -forward

| usage | description |
|-----|-----|
| -forward \<seconds\> | Moves the playback position forward by x seconds |

#### -back

| usage | description |
|-----|-----|
| -back \<seconds\> | Moves the playback position backwards by x seconds |

#### -seek

| usage | description |
|-----|-----|
| -seek \<milliseconds\> | Jumps to the position x of the current resource |

### EQ Commands

#### -eqstart

| usage | description |
|-----|-----|
| -eqstart | Engages the EQ and updates the playback on the fly |

#### -eqstop

| usage | description |
|-----|-----|
| -eqstop | Disengages the EQ on the fly |

#### -eqband

| usage | description |
|-----|-----|
| -eqband \<band\> \<value\> | Sets the given band of the EQ to the given value up or down |

#### -eqhighbass

| usage | description |
|-----|-----|
| -eqhighbass \<value\> | Adds gain to the higher bands of the EQ |

#### -eqlowbass

| usage | description |
|-----|-----|
| -eqlowbass \<value\> | Adds gain to the lower bands of the EQ |

#### -bassboost

| usage | description |
|-----|-----|
| -bassboost \<value\> | Engages bass boost (prepare for earrape) |

### NSFW Commands

#### -hentai / -rule34

| usage | description |
|-----|-----|
| -rule34 \<amount\> [tags] | Pulls Hentai images from Rule34. |
| -hentai \<amount\> [tags] | Pulls Hentai images from Rule34. |

#### -real

| usage | description |
|-----|-----|
| -real \<amount\> [tags] | Pulls Real images from Realbooru. |

#### -safe

| usage | description |
|-----|-----|
| -safe \<amount\> [tags] | Pulls SFW images from Safebooru. |

### Bot settings 

#### -setvc

| usage | description |
|-----|-----|
| -setvc \<voice_channel_id\> | Sets the voice channel the bot should use. Normally it uses the default one / first one. |

#### -setdebug

| usage | description |
|-----|-----|
| -setdebug \<true/false\> | Enables / Disabled the debug mode of the bot. While oil debug mode every exception the bot encounters will be logged with a stack trace as a discord message |

### Queue Management 

#### -savequeue

| usage | description |
|-----|-----|
| -savequeue \<name\> | Safes the current queue to a local file for later use (for some reason sometimes it only saves up to 8 tracks / problem under investigation) |

#### -loadqueue

| usage | description |
|-----|-----|
| -loadqueue \<name\> | Loads a previously saved queue |

#### -listqueues

| usage | description |
|-----|-----|
| -listqueues | Lists all saved queues and their names |

### Administrative commands

#### -addadmin

| usage | description |
|-----|-----|
| -addadmin \<user_id\> | Promotes a user to admin status. Note that this user will be admin on all guilds where this instance of the bot is running. |

#### -removeadmin

| usage | description |
|-----|-----|
| -removeadmin \<user_id\> | Removes an admin from the bot settings. Note that this user will be admin on all guilds where this instance of the bot is running |


#### -listadmins

| usage | description |
|-----|-----|
| -listadmins | Lists all admin of this bot's instance |

##### Hint

To restrict commands add their names (without the prefix) to the config.json file! After that only admins have permission to execute these commands!

### Miscellaneous commands

#### -setprefix

| usage | description |
|-----|-----|
| -setprefix \<prefix_character\> | Changes the command prefix of the bot. NOTE: This change takes effect immediately (no restart required!) |


#### -setaurl

| usage | description |
|-----|-----|
| -setaurl \<avatar url\> | Updates the bot's avatar. May be Rate limited (Don't use too often) |




‎







<!--stackedit_data:
eyJoaXN0b3J5IjpbMTYyNDMwMTIzMywtMTQxMDg4MTc2NSwtMj
EzMjEwOTU2NywxOTE3OTA2NTgsLTE5OTUzNTUzNjgsNTIzMDU2
OTg0LDE1NDkyMjk3NDNdfQ==
-->