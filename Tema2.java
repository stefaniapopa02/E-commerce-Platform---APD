import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

public class Tema2 {
    public static void main(String[] args)  {

        String inputFolder = args[0]; // folderul de input
        int P = Integer.parseInt(args[1]); // numarul de thread-uri

        // daca nu exista fisiere de iesire le dam create si daca exista le golim.
        File ordersOutFile = new File("orders_out.txt");
        if (!ordersOutFile.exists()) {
            try {
                ordersOutFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            ordersOutFile.delete();
            try {
                ordersOutFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        File orderProductsOutFile = new File("order_products_out.txt");
        if (!orderProductsOutFile.exists()) {
            try {
                orderProductsOutFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            orderProductsOutFile.delete();
            try {
                orderProductsOutFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // fisierele de intrare
        File ordersFile = new File(inputFolder + "/orders.txt");
        File productsFile = new File(inputFolder + "/order_products.txt");

        // numele fisierului de orders
        String ordersFilename = inputFolder + "/orders.txt";

        // obiecte folosite pentru sincronizarea scrierii
        Object orderWritingSync = new Object();
        Object productsWritingSync = new Object();

        // cele executorservices(unul pentru comenzi, altul pentru produse)
        ExecutorService ordersTP = Executors.newFixedThreadPool(P);
        ExecutorService productsTP = Executors.newFixedThreadPool(P);

        // pentru contorizarea numarului de taskuri din al doilea executorservice.
        AtomicInteger inQueue = new AtomicInteger(0);

        int ordersFileSize = (int) ordersFile.length();

        // calculam indicii pentru fisierul de intrare de orders
        // il impartim pe bytes exact ca pe un vector.
        for (int id = 0; id < P; id++) {
            int startChar = (int) (id * (double) ordersFileSize / P);
            int endChar = (int) Math.min((id + 1) * (double) ordersFileSize / P, ordersFileSize);

            if (id != 0) {
                // deoarece cel de dinainte are startChar.
                startChar += 1;
            }
            if (id == P - 1) {
                // fara ultimul daca este ultimul thread.
                endChar -= 1;
            }

            // pentru prelucrarea in paralel a comenzilor
            inQueue.incrementAndGet();
            ordersTP.submit(new orderTask(ordersFile, startChar, endChar,
                    productsTP, ordersTP, productsFile, P, inQueue, orderWritingSync, productsWritingSync, ordersFilename));
        }
    }

}
