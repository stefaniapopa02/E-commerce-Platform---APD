import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

public class productTask extends Thread {
    File productsFile;
    int start; //linia de start
    int end; //linia de final
    String orderID;
    int productsPerOrder;
    Semaphore semaphore;
    ExecutorService productsTP;
    final Object productsWritingSync;

    public productTask(File productsFile, int start, int end, String orderID, Semaphore semaphore, ExecutorService productsTP, Object productsWritingSync) {
        this.productsFile = productsFile;
        this.start = start;
        this.end = end;
        this.orderID = orderID;
        this.productsPerOrder = end - start; // numarul de linii care trebuie citite de thread
        this.semaphore = semaphore;
        this.productsTP = productsTP;
        this.productsWritingSync = productsWritingSync;
    }

    @Override
    public void run() {
        int lineLength = 26;  //fiecare linie din fisierul cu produse are exact 26 de caractere (incluzand caracterul '\n')
        int offsetStart = start * lineLength; //pozitia de start se calculeaza tinand cont de linia de start
        // si de faptul ca fiecare linie are lineLength caractere

        FileReader fileReader = null;
        try {
            fileReader = new FileReader(productsFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            bufferedReader.skip(offsetStart); //pornim de la pozitia de start

            // citim numarul de linii alocate thread-ului.
            for (int i = 0; i < productsPerOrder; i++) {

                String line = bufferedReader.readLine();
                String[] details = line.split(",");

                // detaliile produsului
                String crtOrderId = details[0];
                String crtProductId = details[1];

                if (crtOrderId.equals(orderID)) { // daca e produsul e pentru comanda threadului

                    synchronized (productsWritingSync) { // sincronizam scrierea in fisierul order_products_out.txt
                        FileWriter fileWriter = new FileWriter("order_products_out.txt", true);
                        fileWriter.write(crtOrderId + "," + crtProductId + ",shipped\n");
                        fileWriter.close();
                    }

                    // dam release pentru un produs din comanda
                    semaphore.release();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

