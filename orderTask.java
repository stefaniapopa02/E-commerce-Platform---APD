import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class orderTask extends Thread {
    File ordersFile;
    int offsetStart;
    int offsetEnd;
    final Object orderWritingSync;
    Object productsWritingSync;
    List<Integer> startLinesP = new ArrayList<Integer>();
    List<Integer> endLinesP = new ArrayList<Integer>();
    ExecutorService productsTP;
    ExecutorService ordersTP;
    String ordersFilename;
    File productsFile;
    int P;
    AtomicInteger inQueue;

    public orderTask(File ordersFile, int start, int end, ExecutorService productsTP, ExecutorService ordersTP,
                     File productsFile, int P, AtomicInteger inQueue,
                     Object orderWritingSync, Object productsWritingSync, String ordersFilename) {

        this.ordersFile = ordersFile;
        this.offsetStart = start;
        this.offsetEnd = end;
        this.productsTP = productsTP;
        this.ordersTP = ordersTP;
        this.productsFile = productsFile;
        this.P = P;
        this.inQueue = inQueue;
        this.ordersFilename = ordersFilename;
        this.orderWritingSync = orderWritingSync;
        this.productsWritingSync = productsWritingSync;

        // impartirea fisierului order_products pe linii
        int lineLength = 26;
        int productsFileSize = (int) productsFile.length();
        int noOfProducts = productsFileSize / lineLength; // = numarul de linii din fisierul cu produse
        for (int id = 0; id < P; id++) {
            int startLine = (int) (id * (double) noOfProducts / P);
            int endLine = (int) Math.min((id + 1) * (double) noOfProducts / P, noOfProducts);

            startLinesP.add(startLine);
            endLinesP.add(endLine);           //pentru prelucrarea in paralel a produselor
        }
    }


    @Override
    public void run() {
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(ordersFilename, "r");
            randomAccessFile.seek(offsetStart); //pornesc de la pozitia de start in fisierul de input

            StringBuilder ordersString = new StringBuilder();

            char c1 = (char) randomAccessFile.readByte(); //analizez primele doua caractere
            char c2 = (char) randomAccessFile.readByte();
            offsetStart += 2;

            // inseamna ca nu ma aflu de la inceputul liniei, deci trebuie sa merg la dreapta in fisier
            if (c1 != 'o' || c2 != '_') {
                if (c1 == '\n') {
                    // inseamna ca ma aflu cu c2 la inceputul liniei
                    ordersString.append(c2);
                } else if (c2 != '\n') {
                    // daca c2 nu este chiar newline, adica ma aflu chiar la inceputul liniei
                    while (true) {
                        char c = (char) randomAccessFile.readByte();
                        offsetStart += 1;
                        if (c == '\n') {
                            break;
                        }
                    }
                }

            } else {
                ordersString.append(c1).append(c2);
            }

            // citim caracterele care au ramas.
            for (int i = 0; i < offsetEnd - offsetStart + 1; i++) {
                ordersString.append((char) randomAccessFile.readByte());
            }

            // in caz ca nu ne aflam la sfarsit de linie, mergem la dreapta pana dam de \n
            if (ordersString.length() != 0) {
                if (ordersString.charAt(ordersString.length() - 1) != '\n') {
                    while (true) {
                        char c = (char) randomAccessFile.readByte();
                        if (c == '\n') {
                            break;
                        }
                        ordersString.append(c);
                    }
                }
            }

            String ordersStringRes = ordersString.toString(); // partea din fisierul de comenzi pe care a citit-o threadul

            if (ordersStringRes.length() != 0) {

                // luam fiecare comanda citita in parte
                String[] orders = ordersStringRes.split("\n");

                for (int i = 0; i < orders.length; i++) {

                    String[] details = orders[i].split(",");

                    // detaliile comenzii(id si numar produse)
                    String orderID = details[0];
                    int totalNoProducts = Integer.parseInt(details[1]);

                    if (totalNoProducts != 0) {  //ne ocupam de o comanda doar daca aceasta contine cel putin un produs

                        Semaphore semaphore = new Semaphore(-totalNoProducts + 1);

                        for (int p = 0; p < startLinesP.size(); p++) { // prelucram in paralel comanda cu mai multe threaduri productTask
                             productsTP.submit(new productTask(productsFile, startLinesP.get(p), endLinesP.get(p), orderID, semaphore, productsTP, productsWritingSync));
                        }

                        // comanda se considera a fi livrata doar daca toate produsele componente au fost deja livrate
                        semaphore.acquire();

                        synchronized (orderWritingSync) { // sincronizam scrierea in fisierele de iesire
                            // astfel incat un singur thread poate scrie la un moment in unul dintre fisiere
                            FileWriter fileWriter = new FileWriter("orders_out.txt", true);
                            fileWriter.write(orderID + "," + totalNoProducts + ",shipped\n");
                            fileWriter.close();
                        }

                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        int left = inQueue.decrementAndGet();// s-a mai terminat de procesat o comanda,
        // daca nu a mai ramas niciuna vom opri ambele threadpooluri
        if(left == 0) {
            productsTP.shutdown();
            ordersTP.shutdown();
        }

    }
}
