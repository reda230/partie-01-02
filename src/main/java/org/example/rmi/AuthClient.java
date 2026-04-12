package org.example.rmi;

import java.rmi.Naming;
import java.util.Scanner;

public class AuthClient {
    public static void main(String[] args) {
        try {
            AuthService auth = (AuthService) Naming.lookup("rmi://localhost/AuthService");
            Scanner sc = new Scanner(System.in);

            while (true) {
                System.out.println("1. Add user");
                System.out.println("2. Delete user");
                System.out.println("3. Update password");

                int choice = sc.nextInt();
                sc.nextLine();

                System.out.print("Username: ");
                String user = sc.nextLine();

                switch (choice) {
                    case 1:
                        System.out.print("Password: ");
                        System.out.println(auth.createUser(user, sc.nextLine()));
                        break;
                    case 2:
                        System.out.println(auth.deleteUser(user));
                        break;
                    case 3:
                        System.out.print("New password: ");
                        System.out.println(auth.updatePassword(user, sc.nextLine()));
                        break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}