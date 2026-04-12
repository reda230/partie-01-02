package org.example.rmi;

import org.example.db.DatabaseManager;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Partie 5 — AuthServiceImpl utilise MySQL via DatabaseManager.
 * Remplace l'ancienne implémentation basée sur users.json.
 */
public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {

    protected AuthServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public boolean authenticate(String username, String password) throws RemoteException {
        try {
            return DatabaseManager.getInstance().authenticate(username, password);
        } catch (Exception e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean createUser(String username, String password) throws RemoteException {
        try {
            return DatabaseManager.getInstance().createUser(username, password);
        } catch (Exception e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteUser(String username) throws RemoteException {
        try {
            return DatabaseManager.getInstance().deleteUser(username);
        } catch (Exception e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updatePassword(String username, String newPassword) throws RemoteException {
        try {
            return DatabaseManager.getInstance().updatePassword(username, newPassword);
        } catch (Exception e) {
            throw new RemoteException("DB error: " + e.getMessage(), e);
        }
    }
}