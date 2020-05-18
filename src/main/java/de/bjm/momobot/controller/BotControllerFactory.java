package de.bjm.momobot.controller;

import de.bjm.momobot.BotApplicationManager;
import de.bjm.momobot.BotGuildContext;
import net.dv8tion.jda.api.entities.Guild;

public interface BotControllerFactory<T extends BotController> {
  Class<T> getControllerClass();

  T create(BotApplicationManager manager, BotGuildContext state, Guild guild);
}
