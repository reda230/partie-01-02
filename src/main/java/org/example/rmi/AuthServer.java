package org.example.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class AuthServer {
    public static void main(String[] args) {
        try {
            AuthService service = new AuthServiceImpl();

            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("AuthService", service);

            System.out.println("RMI Auth Server running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}