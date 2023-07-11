package com.example.book.service.impl;

import com.example.book.DTO.UserWithRoleDTO;
import com.example.book.model.*;
import com.example.book.notfound.NotFoundException;
import com.example.book.respository.BookRepository;
import com.example.book.respository.RoleRepository;
import com.example.book.respository.UserRepository;
import com.example.book.service.BillService;
import com.example.book.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BillService billService;
private final RoleRepository roleRepository;

    public UserServiceImpl(UserRepository userRepository, BookRepository bookRepository, BillService billService, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.billService = billService;
        this.roleRepository = roleRepository;
    }

    @Override
    public List<Book> getBorrowedBooks(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        List<Bill> bills = billService.getBillsByUser(user);
        List<Book> borrowedBooks = new ArrayList<>();
        for (Bill bill : bills) {
            List<Book> books = billService.getBooksByBill(bill);
            borrowedBooks.addAll(books);
        }
        return borrowedBooks;
    }

    @Override
    public void borrowBook(Long userId, Long bookId, Integer quantity) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Book book = bookRepository.findById(bookId).orElseThrow(() -> new NotFoundException("Book not found"));
        List<Bill> activeBills = billService.getActiveBillsByUser(user);
        if (activeBills.size() >= 3) {
            throw new NotFoundException("You have reached the maximum limit for borrowing books");
        }

        // Kiểm tra số lượng sách trong kho còn đủ để mượn hay không
        if (book.getInventory() <= 0) {
            throw new NotFoundException("The book is out of stock");
        }

        // Kiểm tra logic và điều kiện cho việc mượn sách

        Bill bill = billService.getActiveBillByUser(user);
        int borrowedBooks = 0;
        if (bill == null) {
            bill = new Bill();
            bill.setUser(user);
            bill.setBorrowDate(LocalDate.now());
            bill.setReturnDate(LocalDate.now().plusDays(20));
            billService.saveBill(bill);
        } else {
            // Kiểm tra số lượng sách đã mượn trong bill
            List<BillDetails> billDetailsList = billService.getBillDetailsByBill(bill);
            borrowedBooks = billDetailsList.stream()
                    .mapToInt(BillDetails::getQuantity)
                    .sum();
            if (borrowedBooks >= 3) {
                throw new NotFoundException("You have reached the maximum limit for borrowing books");
            }
        }


        BillDetails billDetails = new BillDetails();
        billDetails.setBill(bill);
        billDetails.setBook(book);
        billDetails.setQuantity(quantity);
        billService.saveBillDetails(billDetails);
        // giảm giá trị tong kho ;

        int newInventory = book.getInventory() - 1;
        book.setInventory(newInventory);
        bookRepository.save(book);

        // Tính toán giá trị của trường "sum_price" dựa trên 30% giá tiền sách và số sách mà khách hàng mượn
        BigDecimal bookPrice = book.getPrice();
        int totalBorrowedBooks = borrowedBooks + 1;
        BigDecimal sumPrice = bookPrice.multiply(new BigDecimal(0.2)).multiply(new BigDecimal(totalBorrowedBooks));
        bill.setSumPrice(sumPrice);
        billService.saveBill(bill);
    }
/*
@Override
    public void returnBook(Long userId, Long bookId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        Book book = bookRepository.findById(bookId).orElseThrow(() -> new NotFoundException("Book not found"));

        // Kiểm tra hóa đơn mượn sách đang hoạt động của người dùng
        Bill activeBill = getActiveBillByUser(user);
        if (activeBill == null) {
            throw new NotFoundException("No active borrow bill found for the user");
        }
        Bill bill = billService.getActiveBillByUser(user);
        List<BillDetails> billDetailsList;

        if (bill != null) {
            // Lấy danh sách chi tiết sách từ hóa đơn
            billDetailsList = billService.getBillDetailsByBill(bill);

            // Kiểm tra chi tiết sách trong hóa đơn
            BillDetails billDetailsToRemove = null;
            for (BillDetails billDetails : billDetailsList) {
                if (billDetails.getBook().getBookId().equals(book.getBookId())) {
                    billDetailsToRemove = billDetails;
                    break;
                }
            }
        } else {
            throw new NotFoundException("bill not found");
        }

        if (billDetailsToRemove == null) {
            throw new NotFoundException("Book is not borrowed by the user");
        }

        // Xóa chi tiết sách khỏi hóa đơn
        removeBillDetails(billDetailsToRemove);

        // Tăng giá trị tồn kho sách
        int newInventory = book.getInventory() + 1;
        book.setInventory(newInventory);
        bookRepository.save(book);

        // Cập nhật tổng giá trị hóa đơn
        BigDecimal bookPrice = book.getPrice();
        int totalBorrowedBooks = billDetailsList.size() - 1;
        BigDecimal sumPrice = bookPrice.multiply(new BigDecimal(0.2)).multiply(new BigDecimal(totalBorrowedBooks));
        activeBill.setSumPrice(sumPrice);
        saveBill(activeBill);


    }
*/


    @Override
    public BigDecimal calculateLatePaymentFee(int daysLate) {
        BigDecimal latePaymentFeePerDay = new BigDecimal(10);
        return latePaymentFeePerDay.multiply(new BigDecimal(daysLate));
    }





    @Override
    public User findByUserName(String userName) {
        return userRepository.findByUserName(userName);
    }

    @Override
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public User addUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    @Override
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public ResponseEntity<String> loginUser(String email, String password) {
        User user = userRepository.findByEmail(email);
        if (user != null && user.getPassword().equals(password)) {
            return new ResponseEntity<>("Login successful", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public List<User> getAllUser() {
        return userRepository.findAll();
    }

    @Override
    public boolean existsByEmail(String email) {
        User user = userRepository.findByEmail(email);
        return user != null;
    }

    @Override
    public User findById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }
}



