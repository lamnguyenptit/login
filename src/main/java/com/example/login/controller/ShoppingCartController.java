package com.example.login.controller;

import com.example.login.error.ProductNotFoundException;
import com.example.login.error.ShoppingCartException;
import com.example.login.error.UserNotFoundException;
import com.example.login.export.CartCsvExporter;
import com.example.login.export.CartPdfExporter;
import com.example.login.model.*;
import com.example.login.model.dto.CartAndProductDto;
import com.example.login.model.dto.CarDto;
import com.example.login.model.dto.ProductDto;
import com.example.login.model.dto.UserCartDto;
import com.example.login.service.ProductService;
import com.example.login.service.ShoppingCartService;
import com.example.login.service.UserService;
import com.example.login.service.impl.ShoppingCartServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ShoppingCartController {

    @Autowired
    private UserService userService;

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private ProductService productService;

    @Autowired
    private JavaMailSender emailSender;

    @GetMapping("/cart")
    public String viewFirstCart(Model model, HttpServletRequest request, RedirectAttributes redirectAttributes) {
         return viewCart(model, request, redirectAttributes,"1", "id", "asc");
    }

    @GetMapping("/cart/page/{pageNum}")
    public String viewCart(Model model, HttpServletRequest request, RedirectAttributes redirectAttributes,
                           @PathVariable("pageNum") String pageNum, String sortField, String sortDir) {
        try{
            User user = getAuthenticatedUser(request);
            Cart cart = shoppingCartService.getCartByUser(user.getId());
            Boolean checkNullCart = true;

            if(cart == null){
                model.addAttribute("checkNullCart", checkNullCart);
                return "shopping-cart";
            }

            int currentPage = Integer.parseInt(pageNum);
            Page<CartAndProduct> page = shoppingCartService.listProductByUserCart(cart.getId(), currentPage, sortField, sortDir);
            List<CartAndProduct> listProductByUserCart = page.getContent();


            Integer totalItem = listProductByUserCart.size();

            if(totalItem <= 0 ){
                model.addAttribute("checkNullCart", checkNullCart);
                return "shopping-cart";
            }

            Float totalMoney = 0.0F;

            for(CartAndProduct products : listProductByUserCart){
                totalMoney += products.getSubTotal();
            }
            checkNullCart = false;

            model.addAttribute("totalItems", page.getTotalElements());
            model.addAttribute("totalPages", page.getTotalPages());
            model.addAttribute("currentPage", currentPage);
            model.addAttribute("sortField", sortField);
            model.addAttribute("sortDir", sortDir);
            model.addAttribute("checkNullCart", checkNullCart);
            model.addAttribute("listProductByUserCart", listProductByUserCart);
            model.addAttribute("totalMoney", totalMoney);
            model.addAttribute("keyword", null);
            model.addAttribute("moduleURL", "/cart");
            model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");
            long startCount = (currentPage - 1) * ShoppingCartServiceImpl.PRODUCT_PER_PAGE;
            long endCount = Math.min(startCount + ShoppingCartServiceImpl.PRODUCT_PER_PAGE, page.getTotalPages());
            model.addAttribute("startCount", startCount);
            model.addAttribute("endCount", endCount);
            return "shopping-cart";
        }catch (UserNotFoundException us){
            redirectAttributes.addFlashAttribute("message", "B???n c???n ph???i ????ng nh???p");
            return "redirect:/view";
        }catch (ProductNotFoundException pr){
            redirectAttributes.addFlashAttribute("message", "C?? s???n ph???m ???? b??? x??a ho???c kh??ng t???n t???i");
            return "redirect:/view";
        }
    }


    public User getAuthenticatedUser(HttpServletRequest request) throws UserNotFoundException {
        Object principal = request.getUserPrincipal();
        String userEmail = null;
        if(principal == null) userEmail = null;
        else
            userEmail = request.getUserPrincipal().getName();

        if(userEmail == null)
            throw new UserNotFoundException("Kh??ng t???n t???i kh??ch h??ng!");
        User user = userService.getUserByEmail(userEmail);

        return userService.getUserByEmail(userEmail);
    }


    @PostMapping("/cart/add/{productId}/{quantity}")
    @ResponseBody
    public String addToCart(@PathVariable("productId") String productId, @PathVariable("quantity") String quantity,
                            HttpServletRequest request){
       try{
           User user = getAuthenticatedUser(request);
           if(checkProductFound(Integer.parseInt(productId)) == false)
               return "S???n ph???m kh??ng t???n t???i";
           if(user.getRole().equals(Role.ADMIN))
               return "B???n c???n ????ng nh???p ????? th??m s???n ph???m n??y v??o gi??? h??ng";
           if(quantity == null)
               return "C???n nh???p s??? l?????ng";
           Integer quantityVal;
           try{
               quantityVal = Integer.parseInt(quantity);
           }catch(NumberFormatException e){
               return "S??? l?????ng nh???p kh??ng ????ng ?????nh d???ng";
           }
//           if(bindingResult.hasErrors()){
//               return "???? c?? l???i x???y ra";
//           }
//           if(quantityVal <= 0)
//               redirectAttributes.addFlashAttribute("message", "S??? l?????ng th??m v??o gi??? c???n l???n h??n 0!");

           if(quantityVal <= 0)
               return "S??? l?????ng c???n l???n h??n 0";
           int quantityProductInCartByUser = shoppingCartService.getQuantityProductInCart(user.getId(),Integer.parseInt(productId));
           int pieceValue = productService.getQuantityProduct(Integer.parseInt(productId));

           if(( quantityVal + quantityProductInCartByUser) > pieceValue) {
               if(quantityProductInCartByUser > 0)
                   return "???? c?? "+quantityProductInCartByUser+" s???n ph???m trong gi???\n" +
                           "S??? l?????ng c?? th??? mua ti???p t???i ??a l?? " + (pieceValue - quantityProductInCartByUser) + " s???n ph???m";
               return "T???ng s??? l?????ng mua l???n h??n s??? l?????ng c??n l???i";
           }
           Integer updateQuantity = shoppingCartService.updateQuantity(user.getId(), Integer.parseInt(productId),
                   quantityVal);
           return updateQuantity +  " s???n ph???m ???? c???p nh???t th??nh c??ng v??o gi??? h??ng";

       }
       catch (UserNotFoundException ex){
           return "B???n c???n ????ng nh???p ????? th??m s???n ph???m n??y v??o gi??? h??ng";
       }
       catch(ShoppingCartException se){
           return se.getMessage();
       }
    }



    @GetMapping("/cartAnonymous")
    public String viewCartAnonymous(Model model, HttpServletRequest request,
                                    HttpSession session, RedirectAttributes redirectAttributes){
        try{
            User user = getAuthenticatedUser(request);
        }catch (UserNotFoundException ex) {
            Boolean checkNullCart = true;

            Map<ProductDto, Integer> cartsSession = (Map<ProductDto, Integer>) session.getAttribute("CARTS_SESSION");

            if (cartsSession == null || cartsSession.size() == 0) {
                model.addAttribute("checkNullCart", checkNullCart);
                return "shopping-cartAnonymous";
            }

            Float totalMoney = 0.0F;
            for (Map.Entry<ProductDto, Integer> element : cartsSession.entrySet()) {
                if(productService.checkProductIsDelete(element.getKey().getId())){
                    redirectAttributes.addFlashAttribute("message", "C?? s???n ph???m ???? b??? x??a ho???c kh??ng t???n t???i. Mua l???i");
                    return "redirect:/view";
                }

                ProductDto productDto = element.getKey();
                productDto.setOrderQuantity(element.getValue());
                totalMoney += productDto.getSubTotal();
                cartsSession.put(productDto, element.getValue());
            }

            request.getSession().setAttribute("CARTS_SESSION", cartsSession);

            checkNullCart = false;
            model.addAttribute("totalMoney", totalMoney);
            model.addAttribute("checkNullCart", checkNullCart);
            model.addAttribute("cartsSession", cartsSession);

            return "shopping-cartAnonymous";
        }

        return "redirect:/cart";
//        return viewCartAnonymousPage(model, request, "1", "id", "asc");
    }


    @GetMapping("/cartAnonymous/page/{pageNum}")
    public String viewCartAnonymousPage(Model model, HttpServletRequest request,
                                        @PathVariable("pageNum") String pageNum, String sortField, String sortDir) {
        Boolean checkNullCart = true;

        Map<ProductDto, Integer> cartsSession = (Map<ProductDto, Integer>)request.getSession().getAttribute("CARTS_SESSION");
        if(cartsSession == null){
            model.addAttribute("checkNullCart", checkNullCart);
            return "shopping-cartAnonymous";
        }


//        int currentPage = Integer.parseInt(pageNum);
//
//        Page<CartAndProduct> page = shoppingCartService.listProductByUserCart(cart.getId(), currentPage, sortField, sortDir);
//
//
//        List<CartAndProduct> listProductByUserCart = page.getContent();
//
//
//        Integer totalItem = listProductByUserCart.size();
//
//        if(totalItem <= 0 ){
//            model.addAttribute("checkNullCart", checkNullCart);
//            return "shopping-cart";
//        }
//
//        Float totalMoney = 0.0F;
//
//        for(CartAndProduct products : listProductByUserCart){
//            totalMoney += products.getSubTotal();
//        }
//        checkNullCart = false;
//
//        model.addAttribute("totalItems", page.getTotalElements());
//        model.addAttribute("totalPages", page.getTotalPages());
//        model.addAttribute("currentPage", currentPage);
//        model.addAttribute("sortField", sortField);
//        model.addAttribute("sortDir", sortDir);
//        model.addAttribute("checkNullCart", checkNullCart);
//        model.addAttribute("listProductByUserCart", listProductByUserCart);
//        model.addAttribute("totalMoney", totalMoney);
//        model.addAttribute("keyword", null);
//        model.addAttribute("moduleURL", "/cart");
//        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");
//        long startCount = (currentPage - 1) * ShoppingCartServiceImpl.PRODUCT_PER_PAGE;
//        long endCount = Math.min(startCount + ShoppingCartServiceImpl.PRODUCT_PER_PAGE, page.getTotalPages());
//        model.addAttribute("startCount", startCount);
//        model.addAttribute("endCount", endCount);

        model.addAttribute("cartsSession", cartsSession);

        return "shopping-cartAnonymous";
    }

    @PostMapping("/cartAnonymous/add/{productId}/{quantity}")
    @ResponseBody
    public String addToCartAnonymous(@PathVariable("productId") String productId, @PathVariable("quantity") String quantity,
                                     @NotNull HttpServletRequest request) throws ProductNotFoundException {
        Map<ProductDto, Integer> cartsSession = (Map<ProductDto, Integer>) request.getSession().getAttribute("CARTS_SESSION");

        if(cartsSession == null){
            cartsSession = new HashMap<>();
            request.getSession().setAttribute("CARTS_SESSION", cartsSession);
        }

        if(checkProductFound(Integer.parseInt(productId)) == false)
            return "S???n ph???m kh??ng t???n t???i";
        if(quantity == null)
            return "C???n nh???p s??? l?????ng";
        Integer quantityVal;
        try{
            quantityVal = Integer.parseInt(quantity);
        }catch(NumberFormatException e){
            return "S??? l?????ng nh???p kh??ng ????ng ?????nh d???ng";
        }
        if(quantityVal <= 0)
            return "S??? l?????ng c???n l???n h??n 0";

//        Product product = new Product(Integer.parseInt(productId));
        Product product = productService.getProduct(Integer.parseInt(productId));
        ProductDto productDto = productService.convertToProductDto(product);

        Integer numberProductAnonymous = cartsSession.get(productDto) == null? 0 : cartsSession.get(productDto);

        int pieceValue = productService.getQuantityProduct(Integer.parseInt(productId));

        if(( quantityVal + numberProductAnonymous) > pieceValue) {
            if(numberProductAnonymous > 0)
                return "???? c?? "+numberProductAnonymous+" s???n ph???m trong gi???\n" +
                        "S??? l?????ng c?? th??? mua ti???p t???i ??a l?? " + (pieceValue - numberProductAnonymous) + " s???n ph???m";
            return "T???ng s??? l?????ng mua l???n h??n s??? l?????ng c??n l???i";
        }

        Integer updateQuantity = numberProductAnonymous + quantityVal;
        productDto.setOrderQuantity(updateQuantity);

        cartsSession.put(productDto, updateQuantity);

        request.getSession().setAttribute("CARTS_SESSION", cartsSession);
        return updateQuantity +  " s???n ph???m ???? c???p nh???t th??nh c??ng v??o gi??? h??ng";
    }

    @PostMapping("/cart/remove/{productId}")
    @ResponseBody
    public String removeFromCart(@PathVariable("productId") String productId,
                               HttpServletRequest request){
        try{
            User user = getAuthenticatedUser(request);
            shoppingCartService.removeProductFromCart(user.getId(), Integer.parseInt(productId));
            return "???? x??a s???n ph???m th??nh c??ng!";
        }catch (UserNotFoundException enf){
            return "B???n c???n ph???i ????ng nh???p ????? lo???i b??? s???n ph???m";
        }
    }

//    @PostMapping("/cart/update/{productId}/{quantity}")
//    @ResponseBody
//    public String updateProductByUser(@PathVariable("productId") String productId,
//                                    @PathVariable("quantity") String quantity, HttpServletRequest request){
//        try{
//            User user = getAuthenticatedUser(request);
//            Float subTotal = shoppingCartService.updateSubTotal(user.getId(), Integer.parseInt(productId), Integer.parseInt(quantity));
//            return String.valueOf(subTotal);
//        }catch (UserNotFoundException unf){
//            return "B???n c???n ph???i ????ng nh???p ????? ch???nh s???a";
//        }
//    }

    @PostMapping("/cart/update/{productId}/{quantity}")
    @ResponseBody
    public String updateProductByUser(@PathVariable("productId") String productId,
                                      @PathVariable("quantity") String quantity, HttpServletRequest request){
        try{
            User user = getAuthenticatedUser(request);
            shoppingCartService.updateSubTotal(user.getId(), Integer.parseInt(productId), Integer.parseInt(quantity));
//            return String.valueOf(subTotal);
            return "success";
        }catch (UserNotFoundException unf){
            return "B???n c???n ph???i ????ng nh???p ????? ch???nh s???a";
        }
    }

    @PostMapping("/cartAnonymous/update/{productId}/{quantity}")
    @ResponseBody
    public String updateCartAnonymous(@PathVariable("productId") String productId,
                                      @PathVariable("quantity") String quantity, HttpServletRequest request) throws ProductNotFoundException {
            Map<ProductDto, Integer> cartSessions = (Map<ProductDto, Integer>) request.getSession().getAttribute("CARTS_SESSION");
            Product product = productService.getProduct(Integer.parseInt(productId));
            ProductDto productDto = productService.convertToProductDto(product);

            cartSessions.put(productDto, Integer.parseInt(quantity));
            request.getSession().setAttribute("CARTS_SESSION", cartSessions);

            return "success";
    }

    @PostMapping("/cartAnonymous/remove/{productId}")
    @ResponseBody
    public String removeProductInCartAnonymous(@PathVariable("productId") String productId, HttpServletRequest request){
        Map<ProductDto, Integer> cartsSession = (Map<ProductDto, Integer>) request.getSession().getAttribute("CARTS_SESSION");
        ProductDto productDto = new ProductDto(Integer.parseInt(productId));
        cartsSession.remove(productDto);

        request.getSession().setAttribute("CARTS_SESSION", cartsSession);
        return "success";
    }

    @PostMapping("/cart/updatebeforcheckout/{productId}/{quantity}")
    public void updateBeforeCheckout(@PathVariable("productId") String productId,
                                     @PathVariable("quantity") String quantity, HttpServletRequest request){
        try{
            User user = getAuthenticatedUser(request);
            shoppingCartService.updateSubTotal(user.getId(), Integer.parseInt(productId), Integer.parseInt(quantity));
        }catch (UserNotFoundException unf){
        }
    }

    @GetMapping("/prepareCheckout")
    @ResponseBody
    public String prepareCheckout(@RequestParam("productId") String productId, @RequestParam("productQuantity") String productQuantity,
                                  @RequestParam("checkIns") String checkIns, HttpServletRequest request){
        try{
            User user = getAuthenticatedUser(request);
            if(Boolean.valueOf(checkIns) == false)
                return "Kh??ng c?? s???n trong kho v???i s???n ph???m ID"+productId;
            if(productQuantity == null)
                return "B???n c???n nh???p s??? l?????ng v???i s???n ph???m ID"+productId;
            Integer quantityVal;
            try{
                quantityVal = Integer.valueOf(productQuantity);
            }catch(NumberFormatException e){
                return "S??? l?????ng nh???p kh??ng ????ng ?????nh d???ng v???i s???n ph???m ID"+productId;
            }
            if(quantityVal<=0)
                return "S??? l?????ng nh???p c???n l???n h??n 0 v???i s???n ph???m ID"+productId;
            int pieceValue = productService.getQuantityProduct(Integer.parseInt(productId));
            if(quantityVal > pieceValue)
                return "T???ng s??? l?????ng mua l???n h??n s??? l?????ng c??n l???i v???i s???n ph???m ID" + productId;
            else
                shoppingCartService.updateSubTotal(user.getId(), Integer.parseInt(productId), quantityVal);
            return "";
        }catch (UserNotFoundException unf){
            return "B???n c???n ????ng nh???p ????? thanh to??n";
        }
    }



    @GetMapping("/checkout")
    @ResponseBody
    public String checkoutCart(HttpServletRequest request) throws ProductNotFoundException {
        try{
            User user = getAuthenticatedUser(request);
//            if(user.getVerificationCodeCheckout() != null || user.getVerificationCodeCheckout() == "")
//                return "B???n v???n c??n ????n h??ng ??ang ch??? thanh to??n";
            int cartId = shoppingCartService.checkOutCart(user.getId());
            sendEmailVerityCheckOut(request, user, cartId);
        }catch (UserNotFoundException unf){
            return "B???n c???n ph???i ????ng nh???p ????? thanh to??n";
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "B???n ???? ?????t h??ng th??nh c??ng\nKi???m tra email ????? xem th??ng tin chi ti???t ????n h??ng";
    }

    @GetMapping("/prepareCheckoutAnonymous")
    @ResponseBody
    public String prepareCheckoutAnonymous(@RequestParam("productId") String productId, @RequestParam("productQuantity") String productQuantity,
                                  @RequestParam("checkIns") String checkIns, HttpServletRequest request){
            if(Boolean.valueOf(checkIns) == false)
                return "Kh??ng c?? s???n trong kho v???i s???n ph???m ID"+productId;
            if(productQuantity == null)
                return "B???n c???n nh???p s??? l?????ng v???i s???n ph???m ID"+productId;
            Integer quantityVal;
            try{
                quantityVal = Integer.valueOf(productQuantity);
            }catch(NumberFormatException e){
                return "S??? l?????ng nh???p kh??ng ????ng ?????nh d???ng v???i s???n ph???m ID"+productId;
            }
            if(quantityVal<=0)
                return "S??? l?????ng nh???p c???n l???n h??n 0 v???i s???n ph???m ID"+productId;
            int pieceValue = productService.getQuantityProduct(Integer.parseInt(productId));
            if(quantityVal > pieceValue)
                return "T???ng s??? l?????ng mua l???n h??n s??? l?????ng c??n l???i v???i s???n ph???m ID" + productId;
            return "";
    }

    @GetMapping("/fillInformation")
    public String fillInformation(Model model, HttpServletRequest request, RedirectAttributes redirectAttributes){
        try{
            User user = getAuthenticatedUser(request);

        }catch(UserNotFoundException ex){
            AnonymousForm anonymousForm = new AnonymousForm();
            model.addAttribute("anonymousForm", anonymousForm);
//        model.addAttribute("message", message);

            return "cart/fill-information";
        }
        redirectAttributes.addFlashAttribute("message", "B???n kh??ng c?? quy???n truy c???p v??o trang n??y. H??y mua h??ng :-))");
        return "redirect:/view";
    }

    @PostMapping("/fillInformation")
    public String CompleteFillInformation(@Validated @ModelAttribute("anonymousForm") AnonymousForm anonymousForm,
                                          BindingResult bindingResult, HttpServletRequest request,
                                          RedirectAttributes redirectAttributes, Model model){
        if(bindingResult.hasErrors())
            return "cart/fill-information";

        try{
            Integer.parseInt(anonymousForm.getPhone());
        }catch (NumberFormatException e){
            redirectAttributes.addFlashAttribute("message", "S??? ??i???n tho???i nh???p kh??ng ????ng ?????nh d???ng. Nh???p l???i");
            return "redirect:/fillInformation";
        }

        if(Integer.parseInt(anonymousForm.getPhone()) <0 ){
            redirectAttributes.addFlashAttribute("message", "S??? ??i???n tho???i nh???p c???n sai!");
            return "redirect:/fillInformation";}

        Map<ProductDto, Integer> cartSessions = (Map<ProductDto, Integer>) request.getSession().getAttribute("CARTS_SESSION");
        if(cartSessions == null || cartSessions.size() == 0)
            return "redirect:/cartAnonymous";
//        if(anonymousForm.isSendMail())
//            sendEmailAnonymous(anonymousForm, request);

        return "redirect:/checkoutAnonymous";
    }

//    public void sendEmailAnonymous(AnonymousForm anonymousForm, HttpServletRequest request) throws MessagingException, UnsupportedEncodingException {
//        Map<ProductDto, Integer> cartsSession = (Map<ProductDto, Integer>) request.getSession().getAttribute("CARTS_SESSION");
//
////        String toAddress = anonymousForm.getEmail();
//        String subject = "Th??ng Tin ????n H??ng";
//        String first_content = "<strong>C??ng ty [[company]]</strong> <br><br>" +
//                "Woo hoo! ????n h??ng c???a b???n ???? ???????c ?????t th??nh c??ng. Chi ti???t ????n h??ng b???n c?? th??? theo d??i d?????i ????y\n" +
//                "<br>" +
//                "T??M T???T ????N H??NG: <br>" +
//                "<br>" +
//                "????n h??ng: PO#[[number]]<br>" +
//                "Ng??y ?????t: [[date]]<br>" +
//                "T???ng ti???n: [[price]]<br>" +
//                "<br>" +
//                "?????A CH??? GIAO H??NG: [[address]]<br>" +
//                "<br>" +
//                "DANH S??CH C??C ????N H??NG: <br>";
//
//        String siteURL = request.getRequestURL().toString();
//        siteURL = siteURL.replace(request.getServletPath(), "");
//
//
//        String second_content = "";
//        Float totalMoney = 0f;
//        for(Map.Entry<ProductDto, Integer> item: cartsSession.entrySet()) {
//            second_content += "<b>S???n ph???m: </b>" + "<a href=" + siteURL + "/p/" + item.getKey().getId() + ">"
//                    + item.getKey().getName() + "</a>";
//            second_content += " <b>S??? l?????ng: </b>" + item.getValue();
//            second_content += " <b>Th??nh ti???n: </b>" + Math.round(item.getKey().getSubTotal()) + "<br>";
//
//            totalMoney += item.getKey().getSubTotal();
//        }
//
//            String third_content = "<br>" +
//                    "C???m ??n b???n ???? tin t?????ng v?? mua h??ng c???a c??ng ty. N???u b???n c?? b???t k??? c??u h???i n??o, li??n h??? v???i ch??ng t??i t???i [[contact]] ho???c g???i tr???c ti???p theo s??? ??i???n tho???i [[phone]]\n" +
//                    "<br>" +
//                    "Th??n ??i,<br>" +
//                    "[[company]]";
//
//            String content = first_content + second_content + third_content;
//            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
//            LocalDateTime now = LocalDateTime.now();
//
//
//            content = content.replace("[[company]]", "FakeBook");
//            content = content.replace("[[date]]", dtf.format(now));
//            content = content.replace("[[price]]",Math.round(totalMoney) +"??");
//            content = content.replace("[[address]]", anonymousForm.getAddress());
//            content = content.replace("[[purchase]]", siteURL+"/purchase");
//            content = content.replace("[[contact]]", siteURL+"/contact");
//            content = content.replace("[[phone]]", "09878889976");
//
//            MimeMessage message = emailSender.createMimeMessage();
//            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
//
//            helper.setFrom("thaixuan.thainguyen@gmail.com", "Van Luc Nguyen");
////            helper.setTo(toAddress);
//            helper.setSubject(subject);
//            helper.setText(content, true);
//
//            emailSender.send(message);
//    }

    @GetMapping("/checkoutAnonymous")
    public String checkoutCartAnonymous(HttpServletRequest request) throws ProductNotFoundException {
        Map<ProductDto, Integer> cartsSession = (Map<ProductDto, Integer>) request.getSession().getAttribute("CARTS_SESSION");
        shoppingCartService.checkoutCartAnonymous(cartsSession);

        request.getSession().invalidate();
        return "redirect:/checkoutAnonymousSuccess";
    }

    @GetMapping("/checkoutAnonymousSuccess")
    public String checkoutAnonymousSuccess(){
        return "cart/checkout-anonymous";
    }

    @GetMapping("/checkoutRequest")
    public String checkoutRequest(){
        return "cart/checkout-request";
    }

    public void sendEmailVerityCheckOut(HttpServletRequest request, User user, int cartId) throws MessagingException, UnsupportedEncodingException {
        CarDto cart = shoppingCartService.getCartDtoById(cartId);
        List<CartAndProductDto> cartAndProductList = shoppingCartService.getCartAndProductsDetail(cartId);

        String toAddress = user.getEmail();
        String subject = "Th??ng Tin ????n H??ng PO"+cartId;
        String first_content = "<strong>C??ng ty [[company]]</strong> <br><br>" +
                "Woo hoo! ????n h??ng c???a b???n ???? ???????c ?????t th??nh c??ng. Chi ti???t ????n h??ng b???n c?? th??? theo d??i d?????i ????y\n" +
                "<br>" +
                "T??M T???T ????N H??NG: <br>" +
                "<br>" +
                "????n h??ng: PO#[[number]]<br>" +
                "Ng??y ?????t: [[date]]<br>" +
                "T???ng ti???n: [[price]]<br>" +
                "<br>" +
                "?????A CH??? GIAO H??NG: [[address]]<br>" +
                "<br>" +
                "DANH S??CH C??C ????N H??NG: <br>";

        int numberOfProduct = cartAndProductList.size();

        String siteURL = request.getRequestURL().toString();
        siteURL = siteURL.replace(request.getServletPath(), "");

        String second_content = "";
        for(int i =0; i<numberOfProduct; i++){
            second_content += "<b>S???n ph???m: </b>"+"<a href=" + siteURL+ "/p/" + cartAndProductList.get(i).getProduct().getId() + ">"
                    + cartAndProductList.get(i).getProduct().getName()+"</a>";
            second_content += " <b>S??? l?????ng: </b>" + cartAndProductList.get(i).getQuantity();
            second_content += " <b>Th??nh ti???n: </b>"+ (int)cartAndProductList.get(i).getSubTotal() +"<br>";

        }

        String third_content = "<b>L???CH S??? MUA H??NG C???A B???N</b> [[purchase]]<br>" +
                "<br>" +
                "C???m ??n b???n ???? tin t?????ng v?? mua h??ng c???a c??ng ty. N???u b???n c?? b???t k??? c??u h???i n??o, li??n h??? v???i ch??ng t??i t???i [[contact]] ho???c g???i tr???c ti???p theo s??? ??i???n tho???i [[phone]]\n" +
                "<br>" +
                "Th??n ??i,<br>" +
                "[[company]]";

        String content = first_content + second_content + third_content;


        content = content.replace("[[company]]", "FakeBook");
        content = content.replace("[[number]]", String.valueOf(cartId));
        content = content.replace("[[date]]", String.valueOf((cart.getCheckoutDate())));
        content = content.replace("[[price]]",Math.round(cart.getTotalMoney()) +"??");
        content = content.replace("[[address]]", String.valueOf(user.getAddress()));
        content = content.replace("[[purchase]]", siteURL+"/purchase");
        content = content.replace("[[contact]]", siteURL+"/contact");
        content = content.replace("[[phone]]", "09878889976");

        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

        helper.setFrom("thaixuan.thainguyen@gmail.com", "Van Luc Nguyen");
        helper.setTo(toAddress);
        helper.setSubject(subject);
        helper.setText(content, true);

        emailSender.send(message);
    }

//    @GetMapping("/verifyCheckout")
//    public String verifyEmailCheckOut(@RequestParam("code") String code, Model model){
//        CheckoutType checkVar = shoppingCartService.verifyCheckOut(code);
//        if(checkVar == CheckoutType.SUCCESS){
//            shoppingCartService.verifyCheckOutSuccess(code);
//            return "cart/checkout-success";
//        }
//        String param = "";
//        if(checkVar == CheckoutType.USER_NOT_FOUND)
//            param = "USER_NOT_FOUND";
//        if(checkVar == CheckoutType.PRODUCT_NOT_FOUND)
//            param = "PRODUCT_NOT_FOUND";
//        if(checkVar == CheckoutType.EXCEED_QUANTITY)
//            param = "EXCEED_QUANTITY";
//
//        model.addAttribute("param", param);
//        return "cart/checkout-failed";
//    }
//    @GetMapping("/failedURL")
//    public String failedUrl(){
//        return "cart/checkout-failed";
//    }


    @GetMapping("/purchase")
    public String firstPagePurchaseOrder(HttpServletRequest request, Model model){

        return viewPurchaseOrder(model, request, "1", "id", "desc" );
    }

    @GetMapping("/purchase/page/{pageNum}")
    public String viewPurchaseOrder(Model model, HttpServletRequest request,
                                    @PathVariable("pageNum") String pageNum, String sortField,
                                    String sortDir){
       try{
           int currentPage = Integer.parseInt(pageNum);
           User user = getAuthenticatedUser(request);
           Boolean checkNullPurchaseOrder = true;


           Page<CarDto> page = shoppingCartService.listCartCheckout(user.getId(), currentPage, sortField, sortDir);
           List<CarDto> listCartPurchase = page.getContent();

           if(listCartPurchase == null){
               model.addAttribute("checkNullPurchaseOrder", checkNullPurchaseOrder);
               return "purchase_order";
           }



           checkNullPurchaseOrder = false;
           model.addAttribute("totalPages", page.getTotalPages());
           model.addAttribute("totalItems", page.getTotalElements());
           model.addAttribute("currentPage", currentPage);
           model.addAttribute("listCartPurchase", listCartPurchase);
           model.addAttribute("sortField", sortField);
           model.addAttribute("sortDir", sortDir);
           model.addAttribute("checkNullPurchaseOrder", checkNullPurchaseOrder);
           model.addAttribute("moduleURL", "/purchase");
           model.addAttribute("keyword", null);
           model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

           int startCount =  ShoppingCartServiceImpl.PRODUCT_PER_PAGE * ( currentPage - 1) + 1;
           model.addAttribute("startCount",startCount);

           long endCount = startCount + ShoppingCartServiceImpl.PRODUCT_PER_PAGE + 1;
           if(endCount > page.getTotalElements())
               endCount = page.getTotalElements();
           model.addAttribute("endCount", endCount);

           return "purchase_order";

       }catch(UserNotFoundException une){
           return "B???n c???n ph???i ????ng nh???p";
       }
    }

    @GetMapping("/cart/export/csv")
    public void exportCartToCsv(HttpServletResponse response, HttpServletRequest request){
       try{
           User user = getAuthenticatedUser(request);
           List<CarDto> cartDTOList = shoppingCartService.listCartDtoToExport(user.getId());

           CartCsvExporter cartCsvExporter = new CartCsvExporter();
           cartCsvExporter.export(cartDTOList, response);
       }catch (UserNotFoundException ex){
           ex.getMessage();
       } catch (IOException e) {
           e.printStackTrace();
       }
    }

    @GetMapping("/cart/export/pdf")
    public void exportCartToPdf(HttpServletResponse response, HttpServletRequest request){
        try{
            User user = getAuthenticatedUser(request);
            List<CarDto> cartDTOList = shoppingCartService.listCartDtoToExport(user.getId());

            CartPdfExporter cartPdfExporter = new CartPdfExporter();
            cartPdfExporter.export(cartDTOList, response);
        }catch (UserNotFoundException ex){
            ex.getMessage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/purchase/order/{cartId}")
    public String viewDetailCart(Model model, HttpServletRequest request, @PathVariable("cartId") String cartId){
        try{
            User user = getAuthenticatedUser(request);
            UserCartDto userCartDto = shoppingCartService.fakeUserCart(user, Integer.parseInt(cartId));
            List<CartAndProductDto> cartAndProductDtoList = shoppingCartService.getCartAndProductsDetail(Integer.parseInt(cartId));

            model.addAttribute("userCartDto", userCartDto);
            model.addAttribute("cartAndProductDtoList", cartAndProductDtoList);
            return "cart_details";
        }catch(UserNotFoundException ex){
            return "B???n c???n ph???i ????ng nh???p";
        }
    }
    public Boolean checkProductFound(int productId) {
       return productService.checkProductIsDelete(productId);
    }
}
