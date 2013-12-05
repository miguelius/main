<?php
/**
 * Account action for MyResearch module
 *
 * PHP version 5
 *
 * Copyright (C) Villanova University 2007.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * @category VuFind
 * @package  Controller_MyResearch
 * @author   Andrew S. Nagy <vufind-tech@lists.sourceforge.net>
 * @author   Demian Katz <demian.katz@villanova.edu>
 * @license  http://opensource.org/licenses/gpl-2.0.php GNU General Public License
 * @link     http://vufind.org/wiki/building_a_module Wiki
 */
require_once "Action.php";
require_once 'sys/Mailer.php'; //Para enviar mail
require_once 'sys/User.php';

require_once 'Mail/RFC822.php';

include_once $_SERVER['DOCUMENT_ROOT'].'/'.$configArray['Captcha']['cap'].'/securimage.php';
/**
 * Account action for MyResearch module
 *
 * @category VuFind
 * @package  Controller_MyResearch
 * @author   Andrew S. Nagy <vufind-tech@lists.sourceforge.net>
 * @author   Demian Katz <demian.katz@villanova.edu>
 * @license  http://opensource.org/licenses/gpl-2.0.php GNU General Public License
 * @link     http://vufind.org/wiki/building_a_module Wiki
 */
class Recover extends Action
{
    /**
     * Constructor
     *
     * @access public
     */
    public function __construct()
    {
    }

    /**
     * Process parameters and display the page.
     *
     * @return void
     * @access public
     */
    public function launch()
    {
        global $interface;
        global $configArray;
		
		 $vurl=$configArray['Site']['url'];
		 $vbiblio=$configArray['Index']['url'];
		 $vstats=$configArray['Statistics']['solr'];
		 $localhost=$configArray['LocalHost']['lht'];
		 $captcha=$configArray['Captcha']['cap'];
		 
		 $admincode=$configArray['Admin']['code'];
	//echo $admincode;
	$local_url=$_SERVER['DOCUMENT_ROOT'];
	
	$interface->assign('captcha', $captcha);
	$interface->assign('localhost', $localhost);
	
        // Don't allow account creation if a non-DB authentication method
        // is being used!!
        if ($configArray['Authentication']['method'] !== 'DB') {
            header('Location: Home');
            die();
        }

        if (isset($_POST['submit'])) {
            $result = $this->_processInput();
            if (PEAR::isError($result)) {
                $interface->assign('message', $result->getMessage());
                $interface->assign('formVars', $_POST);
				if (isset($_POST['type']))
                $interface->setTemplate('recover.tpl');
				else
				 $interface->setTemplate('recover.tpl');
                $interface->display('layout.tpl');
            } else {
                // Now that the account is created, log the user in:
                UserAccount::login();
                header('Location: Home');
                die();
            }
        } else {
            $interface->setPageTitle('Password Recovery');
            $interface->setTemplate('recover.tpl');
            $interface->display('layout.tpl');
        }
    }

    /**
     * Send a record email.
     *
     * @param string $to      Message recipient address
     * @param string $from    Message sender address
     * @param string $message Message to send
     *
     * @return mixed          Boolean true on success, PEAR_Error on failure.
     * @access public
     */
    public function sendEmail($to, $from, $message)
    {
        global $interface;

        $subject = translate("Registro de usuario en LA-Referencia");
        $body = $message;
        $mail = new VuFindMailer();
        return $mail->send($to, $from, $subject, $body);
    }
	
    
    /**
     * Process incoming parameters for account creation.
     *
     * @return mixed True on successful account creation, PEAR_Error otherwise.
     * @access private
     */
    private function _processInput()
    {
	
	   global $configArray;
        // Validate Input
        // if (trim($_POST['username']) == '') {
         //    return new PEAR_Error('Username cannot be blank');
         //}
        // if (trim($_POST['password']) == '') {
         //    return new PEAR_Error('Password cannot be blank');
         //}
        // if ($_POST['password'] != $_POST['password2']) {
        //     return new PEAR_Error('Passwords do not match');
        // }
        if (!Mail_RFC822::isValidInetAddress($_POST['email'])) {
            return new PEAR_Error('Email address is invalid');
        }
		
//$admincode=$configArray['Admin']['code'];
		
        // Create Account
      //   $user = new User();
       //  $user->username = $_POST['username'];
      //   if (!$user->find()) {
            // No username match found -- check for duplicate email:
            $user = new User();
            $user->email = $_POST['email'];
            if ($user->find()) {
				$user->fetch();
				 //session_start();
			     $securimage = new Securimage();
				 //  echo $_SERVER['DOCUMENT_ROOT'] . '/securimage/securimage.php';
				 //  echo ' -- ';
				 // echo $_POST['captcha_code'];echo ' :: ';
				 // print_r( $securimage->getCode(true,true));
				 if ($securimage->check($_POST['captcha_code'])) {
					// We need to reassign the username since we cleared it out when
					// we did the search for duplicate email addresses:
					
					 //$user->username = $_POST['username'];
					 //$user->password = $_POST['password'];
					 //$user->firstname = $_POST['firstname'];
					 //$user->lastname = $_POST['lastname'];
					 
					//$user->laref_country = $_POST['country'];
					
					 //if (isset($_POST['admin']))
					 //$user->laref_country = $_POST['admin'];
				 //	else
					 //$user->laref_country = $_POST['country'];
				 //	$user->laref_institution = $_POST['institution'];
					
					
					 //if (isset($_POST['admin']))
					 // $user->admin_country = $_POST['admin'];
					
					 //$user->created = date('Y-m-d h:i:s');
					
					 //if (isset($_POST['admin']))
					 //	{
						//echo $_POST['authorization'];
						//echo $admincode;
						//if ((strcmp($_POST['authorization'],$admincode)))
						//	return new PEAR_Error('El c&oacute;digo de autorizaci&oacute;n proporcionado no es correcto');
						 //else
						 //	$user->insert();
						 //}
					 //	else
					 //	{
					 //	$user->insert();
					 //	}
					$bienvenida="Datos de registro\nUsuario:".$user->username."\nContrase�a:".$user->password."\nFecha:".date('Y-m-d h:i:s');
					$result2 = $this->sendEmail($user->email, $configArray['Site']['email'], $bienvenida);
				
				} else {
                return new PEAR_Error('El c&oacute;digo de seguridad proporcionado no es correcto');
				}
            } else {
                return new PEAR_Error('El correo proporcionado no est&aacute; registrado');
            }
        // } else {
         //    return new PEAR_Error('El nombre de usuario proporcionado ya est&aacute; en uso');
        // }
        
        return true;
    }
}

?>