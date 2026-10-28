package net.minecraft.world.entity;
import java.util.UUID;
public class Entity { private final UUID id=UUID.randomUUID(); public Entity vehicle; public boolean passenger, riderSit=true; public UUID getUUID(){return id;} public Entity getVehicle(){return vehicle;} public boolean isPassenger(){return passenger;} public boolean shouldRiderSit(){return riderSit;} }
