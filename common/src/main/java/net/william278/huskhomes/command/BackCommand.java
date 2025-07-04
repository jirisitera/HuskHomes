/*
 * This file is part of HuskHomes, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskhomes.command;

import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.position.Location;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.random.NormalDistributionEngine;
import net.william278.huskhomes.teleport.Teleport;
import net.william278.huskhomes.teleport.TeleportBuilder;
import net.william278.huskhomes.user.OnlineUser;
import net.william278.huskhomes.util.TransactionResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class BackCommand extends InGameCommand {
    private final long maxAttempts = 12;
    protected BackCommand(@NotNull HuskHomes plugin) {
        super(
                List.of("back"),
                "",
                plugin
        );
        addAdditionalPermissions(Map.of(
                "death", false,
                "previous", false
        ));
    }
    @Override
    public void execute(@NotNull OnlineUser executor, @NotNull String[] args) {
        final Optional<OnlineUser> optionalTeleporter = args.length >= 1 ? plugin.getOnlineUser(args[0]) : executor instanceof OnlineUser ? Optional.of((OnlineUser) executor) : Optional.empty();
        if (optionalTeleporter.isEmpty()) {
            if (args.length == 0) {
                plugin.getLocales().getLocale("error_invalid_syntax", getUsage())
                    .ifPresent(executor::sendMessage);
                return;
            }
            plugin.getLocales().getLocale("error_player_not_found", args[0])
                .ifPresent(executor::sendMessage);
            return;
        }
        final OnlineUser teleporter = optionalTeleporter.get();
        final Optional<Position> lastPosition = plugin.getDatabase().getLastPosition(executor);
        if (lastPosition.isEmpty()) {
            plugin.getLocales().getLocale("error_no_last_position")
                    .ifPresent(executor::sendMessage);
            return;
        }
        getRandomPosition(lastPosition.get()).thenAccept(position -> {
            if (position.isEmpty()) {
                plugin.getLocales().getLocale("error_rtp_randomization_timeout")
                    .ifPresent(executor::sendMessage);
                return;
            }
            // Build and execute the teleport
            final TeleportBuilder builder = Teleport.builder(plugin)
                .teleporter(teleporter)
                .type(Teleport.Type.BACK)
                .actions(TransactionResolver.Action.BACK_COMMAND)
                .target(position.get());
            builder.buildAndComplete(executor.equals(teleporter), args);
        });
    }
    private CompletableFuture<Optional<Position>> getRandomPosition(@NotNull Location center) {
        return plugin.supplyAsync(() -> {
            Optional<Location> location = generateSafeLocation(center).join();
            int attempts = 0;
            while (location.isEmpty()) {
                location = generateSafeLocation(center).join();
                if (attempts >= maxAttempts) {
                    return Optional.empty();
                }
                attempts++;
            }
            return location.map(resolved -> Position.at(resolved, plugin.getServerName()));
        });
    }
    /**
     * Generate a safe ground-level {@link Location} through a randomized normally-distributed radius and random angle.
     *
     * @return A generated location
     */
    private CompletableFuture<Optional<Location>> generateSafeLocation(@NotNull Location center) {
        return plugin.findSafeGroundLocation(NormalDistributionEngine.generateLocation(center, 0.75F, 2.0F, 200, 600));
    }
}
