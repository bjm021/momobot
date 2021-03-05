package de.bjm.momobot;

import de.bjm.momobot.controller.BotController;

import java.util.HashMap;
import java.util.Map;

/**
 *     Copyright (C) 2020-2021 BJM SoftwareWorks (Benjamin J. Meyer)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 */
public class BotGuildContext {
  public final long guildId;
  public final Map<Class<? extends BotController>, BotController> controllers;

  public BotGuildContext(long guildId) {
    this.guildId = guildId;
    this.controllers = new HashMap<>();
  }
}
