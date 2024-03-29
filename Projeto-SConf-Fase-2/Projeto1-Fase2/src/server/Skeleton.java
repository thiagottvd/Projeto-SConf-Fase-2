package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignedObject;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import domain.BankAccount;
import domain.BankAccount.GroupPaymentReqInformation;
import domain.BankAccount.IndPaymentRequestInformation;
import domain.BankAccountCatalog;
import domain.BlockChain;
import domain.Group;
import domain.GroupCatalog;
import domain.QRCodeGenerator;
import exceptions.GroupExistsException;
import exceptions.GroupNotFoundException;
import exceptions.InsufficientBalanceException;
import exceptions.InvalidGroupOwnerException;
import exceptions.InvalidIdentifierException;
import exceptions.InvalidOperation;
import exceptions.InvalidQrCodeException;
import exceptions.UserAlreadyExistsInGroupException;
import exceptions.UserNotFoundException;

/**
 * 
 * Classe responsavel pela logica de todos os comandos.
 * 
 * @author grupo 36.
 *
 * @param <E> Objeto generico.
 */
@SuppressWarnings("unchecked")
public class Skeleton<E> {

	public E invoke(String userID, BankAccountCatalog bankCatalog, GroupCatalog groupCatalog, String message,
			ObjectInputStream in, ObjectOutputStream out, BlockChain blockChain, String cipherPass)
			throws ClassNotFoundException, IOException {
		E resp = null;
		String[] splittedMessage = message.split(" ", 3);

		BankAccount userBA = null;
		BankAccount otherUserBA = null;
		String otherUserID = null;
		double amount;
		QRCodeGenerator QR = new QRCodeGenerator();
		Group group = new Group();
		SignedObject so = null;
		Certificate certificate = null;

		try {
			userBA = bankCatalog.getBankAccount(userID);
		} catch (UserNotFoundException e) {
			resp = (E) Boolean.FALSE;
		}

		switch (splittedMessage[0]) {
		case "balance":
		case "b":
			if (splittedMessage.length != 1) {
				resp = (E) Boolean.FALSE;
				break;
			}

			resp = (E) String.valueOf(userBA.balance());
			break;
		case "makepayment":
		case "m":
			if (splittedMessage.length != 3) {
				resp = (E) Boolean.FALSE;
				break;
			}

			so = (SignedObject) in.readObject();
			certificate = (Certificate) in.readObject();
			if (!verifySignedObject(so, certificate)) {
				resp = (E) Boolean.FALSE;
				break;
			}

			otherUserID = splittedMessage[1];

			if (userID.equals(otherUserID)) {
				resp = (E) Boolean.FALSE;
				break;
			}

			try {
				otherUserBA = bankCatalog.getBankAccount(otherUserID);
				amount = (double) Double.valueOf(splittedMessage[2]);
				userBA.removeAmount(amount);
				otherUserBA.addAmount(amount);
				resp = (E) Boolean.TRUE;
				blockChain.writeTransaction((String) (so.getObject()), so.getSignature());
			} catch (InsufficientBalanceException | UserNotFoundException e) {
				resp = (E) e.getMessage();
			} catch (NumberFormatException | InvalidOperation e) {
				resp = (E) Boolean.FALSE;
			}
			break;
		case "requestpayment":
		case "r":
			if (splittedMessage.length != 3) {
				resp = (E) Boolean.FALSE;
				break;
			}

			otherUserID = splittedMessage[1];

			if (userID.equals(otherUserID)) {
				resp = (E) Boolean.FALSE;
				break;
			}

			try {
				otherUserBA = bankCatalog.getBankAccount(otherUserID);
				amount = (double) Double.valueOf(splittedMessage[2]);
				otherUserBA.addIndPaymentRequest(otherUserID, userID, amount, cipherPass);
				resp = (E) Boolean.TRUE;
			} catch (UserNotFoundException | NumberFormatException | InvalidOperation e) {
				resp = (E) e.getMessage();
			}
			break;
		case "viewrequests":
		case "v":
			List<String> pendingIndPaymentsList = userBA.getIndPaymtRequestList();
			if (pendingIndPaymentsList == null) {
				resp = (E) "";
				break;
			}

			StringBuilder sb = new StringBuilder();
			int listSize = pendingIndPaymentsList.size();
			for (int i = 0; i < listSize; i++) {
				sb.append(pendingIndPaymentsList.get(i));
				if (!(i == listSize - 1)) {
					sb.append(",");
				}
			}
			resp = (E) sb.toString();
			break;
		case "payrequest":
		case "p":
			if (splittedMessage.length != 2) {
				resp = (E) Boolean.FALSE;
				break;
			}

			so = (SignedObject) in.readObject();
			certificate = (Certificate) in.readObject();
			if (!verifySignedObject(so, certificate)) {
				resp = (E) Boolean.FALSE;
				break;
			}

			try {
				String uniqueID = splittedMessage[1];
				IndPaymentRequestInformation ipri = userBA.getIndPaymentRequestInf(uniqueID, cipherPass);
				amount = ipri.getAmount();
				otherUserID = ipri.getUserWhoRequestedPayment();
				otherUserBA = bankCatalog.getBankAccount(otherUserID);
				userBA.removeAmount(amount);
				otherUserBA.addAmount(amount);
				userBA.removeIndPaymentRequest(uniqueID, userID);
				resp = (E) Boolean.TRUE;
				blockChain.writeTransaction((String) (so.getObject()), so.getSignature());
			} catch (InvalidIdentifierException | UserNotFoundException | InvalidOperation
					| InsufficientBalanceException e) {
				resp = (E) e.getMessage();
			}
			break;
		case "obtainQRcode":
		case "o":
			if (splittedMessage.length != 2) {
				resp = (E) Boolean.FALSE;
				break;
			}

			try {
				amount = (double) Double.valueOf(splittedMessage[1]);
				resp = (E) QR.generateQRCode(userID, amount);
			} catch (Exception e) {
				resp = (E) Boolean.FALSE;
			}
			break;
		case "confirmQRcode":
		case "c":
			if (splittedMessage.length != 2) {
				resp = (E) Boolean.FALSE;
				break;
			}

			// L� informa��o do QRCode e envia para o Cliente assinar
			try {
				String qrCode = splittedMessage[1];
				String info = QR.readQRCode(qrCode);
				if (info == null) {
					throw new InvalidQrCodeException("Nao existe pedido identificado pelo qr code \"" + qrCode + "\".");
				}

				out.writeObject(info);

				so = (SignedObject) in.readObject();
				certificate = (Certificate) in.readObject();
				if (!verifySignedObject(so, certificate)) {
					resp = (E) Boolean.FALSE;
					break;
				}

				String[] parts = info.split("_");
				String otherUser = parts[0];
				String mont = parts[1];

				if (userID.equals(otherUser)) {
					resp = (E) Boolean.FALSE;
					break;
				}

				otherUserBA = bankCatalog.getBankAccount(otherUser);
				amount = (double) Double.valueOf(mont);
				userBA.removeAmount(amount);
				otherUserBA.addAmount(amount);
				resp = (E) Boolean.TRUE;
				blockChain.writeTransaction((String) (so.getObject()), so.getSignature());
			} catch (NumberFormatException | InvalidOperation e) {
				resp = (E) e.getMessage();
			} catch (InsufficientBalanceException | UserNotFoundException | InvalidQrCodeException e) {
				resp = (E) e.getMessage();
			}
			break;
		case "newgroup":
		case "n":
			if (splittedMessage.length != 2) {
				resp = (E) Boolean.FALSE;
				break;
			}

			try {

				bankCatalog.getBankAccount(userID);

				String groupID = splittedMessage[1];
				if (groupCatalog.contains(groupID)) {
					throw new GroupExistsException("Ja existe um grupo com id \"" + groupID + "\".");
				}

				else {
					group.add(userID);
					groupCatalog.add(groupID, group);
					resp = (E) Boolean.TRUE;
				}

			} catch (Exception e) {
				resp = (E) e.getMessage();
			}
			break;
		case "addu":
		case "a":
			if (splittedMessage.length != 3) {
				resp = (E) Boolean.FALSE;
				break;
			}

			try {

				otherUserID = splittedMessage[1];
				bankCatalog.getBankAccount(otherUserID);

				String groupID = splittedMessage[2];
				if (!groupCatalog.contains(groupID)) {
					throw new GroupNotFoundException("Nao existe grupo com id \"" + groupID + "\".");
				}

				Group list = groupCatalog.getGroup(groupID);

				if (!list.isOwner(userID)) {
					throw new InvalidGroupOwnerException("Apenas o dono do grupo pode adicionar um novo membro.");
				} else if (list.contains(otherUserID)) {
					throw new UserAlreadyExistsInGroupException("O usuario indicado ja pertence ao grupo.");
				} else {
					list.add(otherUserID);
					resp = (E) Boolean.TRUE;
				}

			} catch (Exception e) {
				resp = (E) e.getMessage();
			}
			break;

		case "groups":
		case "g":
			if (splittedMessage.length != 1) {
				resp = (E) Boolean.FALSE;
				break;
			}

			try {
				String str1 = "";
				String str2 = "";
				HashMap<String, Group> map = groupCatalog.getGroupList();
				for (String key : map.keySet()) {
					if (map.get(key).isOwner(userID)) {
						str1 = str1 + System.lineSeparator() + key;
					}
				}

				if (str1.equals("")) {
					str1 = "Nao e dono de nenhum grupo.";
				}

				for (String key : map.keySet()) {
					if (map.get(key).contains(userID) && !map.get(key).isOwner(userID)) {
						str2 = str2 + System.lineSeparator() + key;
					}
				}

				if (str2.equals("")) {
					str2 = "Nao e membro de nenhum grupo.";
				}

				str1 = "Grupos (Dono): " + str1 + System.lineSeparator();
				str2 = System.lineSeparator() + "Grupos (Participante): " + str2 + System.lineSeparator();
				String str = str1 + str2;
				resp = (E) str;
			} catch (Exception e) {
				resp = (E) e.getMessage();
			}
			break;

		case "dividepayment":
		case "d":
			if (splittedMessage.length != 3) {
				resp = (E) Boolean.FALSE;
				break;
			}
			try {
				String groupID = splittedMessage[1];
				amount = (double) Double.valueOf(splittedMessage[2]);

				if (!groupCatalog.contains(groupID)) {
					resp = (E) Boolean.FALSE;
					break;
				}

				group = groupCatalog.getGroup(groupID);

				if (!group.isOwner(userID)) {
					resp = (E) Boolean.FALSE;
					break;
				}

				List<String> groupMembers = group.getGroupMembers();
				List<String> pendPayments = new ArrayList<String>();
				double amountPerID = amount / (groupMembers.size());
				for (int i = 0; i < groupMembers.size(); i++) {
					otherUserID = groupMembers.get(i);
					otherUserBA = bankCatalog.getBankAccount(otherUserID);
					IndPaymentRequestInformation paymentInf = otherUserBA.addIndPaymentRequest(otherUserID, userID,
							amountPerID, cipherPass);
					if (paymentInf != null)
						pendPayments.add(paymentInf.getUniqueID());
				}
				userBA.addGroupPaymentRequest(groupID, amount, new ArrayList<String>(groupMembers), pendPayments, cipherPass);

				resp = (E) Boolean.TRUE;
			} catch (UserNotFoundException | NumberFormatException | InvalidOperation e) {
				resp = (E) e.getMessage();
			}
			break;
		case "statuspayments":
		case "s":
			if (splittedMessage.length != 2) {
				resp = (E) Boolean.FALSE;
				break;
			}

			try {
				String groupID = splittedMessage[1];

				group = groupCatalog.getGroup(groupID);

				if (!group.isOwner(userID)) {
					throw new InvalidGroupOwnerException(
							"O usuario com id \"" + userID + "\" nao e dono do grupo \"" + groupID + "\".");
				}

				userBA = bankCatalog.getBankAccount(userID);

				List<GroupPaymentReqInformation> gpriList = userBA.getGroupPaymentReqInformation(groupID);
				if (gpriList != null) {
					for (String currUserID : group.getGroupMembers()) {
						for (GroupPaymentReqInformation gpri : gpriList) {
							BankAccount currUserBA = bankCatalog.getBankAccount(currUserID);
							List<String> paidPendPayments = currUserBA.getPaidPendingPayments();
							List<String> groupPendPayments = gpri.getPendPayments();
							for (String paidPayment : paidPendPayments) {
								for (String groupPendPayment : groupPendPayments) {
									if (paidPayment.equals(groupPendPayment)) {
										gpri.updatePendPaymtsList(paidPayment, currUserID);
										break;
									}
								}
							}
						}
					}
				}
				resp = (E) userBA.statusPayments(groupID);
			} catch (UserNotFoundException | InvalidGroupOwnerException e) {
				resp = (E) e.getMessage();
			}
			break;
		case "history":
		case "h":
			if (splittedMessage.length != 2) {
				resp = (E) Boolean.FALSE;
				break;
			}

			String groupID = splittedMessage[1];

			try {
				group = groupCatalog.getGroup(groupID);

				if (!group.isOwner(userID)) {
					throw new InvalidGroupOwnerException(
							"O usuario com id \"" + userID + "\" nao e dono do grupo \"" + groupID + "\".");
				}

				userBA = bankCatalog.getBankAccount(userID);
				List<String> paymentIds = userBA.getHistory(groupID, cipherPass);

				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.append("ID do grupo: " + groupID);
				stringBuilder.append(System.getProperty("line.separator"));
				stringBuilder.append("Lista de pagamentos realizados do grupo " + groupID + ": ");
				stringBuilder.append(System.getProperty("line.separator"));
				for (String id : paymentIds) {
					stringBuilder.append(id);
					stringBuilder.append(System.getProperty("line.separator"));
				}

				resp = (E) stringBuilder.toString();

			} catch (UserNotFoundException | InvalidOperation | InvalidGroupOwnerException e) {
				resp = (E) e.getMessage();
			}
			break;
		default:
			resp = (E) "Comando nao existe";
			break;
		}

		return resp;
	}

	private boolean verifySignedObject(SignedObject signedObject, Certificate certificate)
			throws ClassNotFoundException, IOException {

		PublicKey publicKey = certificate.getPublicKey();
		try {
			if (signedObject.verify(publicKey, Signature.getInstance("MD5withRSA"))) {
				return true;
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
			e.printStackTrace();
		}
		return false;
	}

	public void rerunOperations(BankAccountCatalog bankCatalog, List<String> operations, String cipherPass) {

		BankAccount userBA = null;
		BankAccount otherUserBA = null;
		double amount;

		for (String op : operations) {

			String[] splittedMessage = op.split(" ", 4);

			switch (splittedMessage[0]) {
			case "makepayment":
			case "m":
				try {
					userBA = bankCatalog.getBankAccount(splittedMessage[1]);
					otherUserBA = bankCatalog.getBankAccount(splittedMessage[2]);
					amount = (double) Double.valueOf(splittedMessage[3]);
					userBA.removeAmount(amount);
					otherUserBA.addAmount(amount);
				} catch (UserNotFoundException | InvalidOperation | InsufficientBalanceException e) {
					e.printStackTrace();
				}
				break;
			case "payrequest":
			case "p":
				String uniqueID = splittedMessage[1];
				IndPaymentRequestInformation ipri;
				try {
					userBA = bankCatalog.getBankAccount(splittedMessage[2]);
					ipri = userBA.getIndPaymentRequestInf(uniqueID, cipherPass);
					amount = ipri.getAmount();
					String otherUserID = ipri.getUserWhoRequestedPayment();
					otherUserBA = bankCatalog.getBankAccount(otherUserID);
					userBA.removeAmount(amount);
					otherUserBA.addAmount(amount);
					userBA.removeIndPaymentRequest(uniqueID, splittedMessage[2]);
				} catch (InvalidIdentifierException | UserNotFoundException | InvalidOperation
						| InsufficientBalanceException e) {
					e.printStackTrace();
				}
				break;
			case "confirmQRcode":
			case "c":
				try {
					userBA = bankCatalog.getBankAccount(splittedMessage[3]);
					otherUserBA = bankCatalog.getBankAccount(splittedMessage[1]);
					amount = (double) Double.valueOf(splittedMessage[2]);
					userBA.removeAmount(amount);
					otherUserBA.addAmount(amount);
				} catch (UserNotFoundException | InvalidOperation | InsufficientBalanceException e) {
					e.printStackTrace();
				}
				break;
			}

		}

	}
}
